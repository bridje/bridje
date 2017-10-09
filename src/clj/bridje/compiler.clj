(ns bridje.compiler
  (:require [bridje.reader :as reader]
            [bridje.analyser :as analyser]
            [bridje.interpreter :as interpreter]
            [bridje.emitter :as emitter]
            [clojure.java.io :as io]
            [clojure.string :as s]))

(defn interpret [s {:keys [global-env current-ns]}]
  (reduce (fn [{:keys [global-env]} form]
            (let [env {:global-env global-env, :current-ns current-ns}]
              (-> form
                  (analyser/analyse env)
                  (interpreter/interpret env))))
          {:global-env global-env}
          (reader/read-forms s)))

(defprotocol CompilerIO
  (slurp-source-file [_ ns-sym])
  (slurp-compiled-file [_ ns-sym])
  (spit-compiled-file [_ ns-sym content]))

(defn real-io [{:keys [source-paths compile-path compiled-file-type]}]
  (letfn [(ns-sym->file-name [ns-sym file-type]
            (str (->> (s/split (name ns-sym) #"\.")
                      (s/join "/"))
                 "." (name file-type)))]

    (reify CompilerIO
      (slurp-source-file [_ ns-sym]
        (let [file-path (ns-sym->file-name ns-sym :brj)]
          (when-let [file (->> (map #(io/file % file-path) source-paths)
                               (filter #(.exists %))
                               first)]
            (slurp file))))

      (slurp-compiled-file [_ ns-sym]
        (let [file (io/file compile-path (ns-sym->file-name ns-sym compiled-file-type))]
          (when (.exists file)
            (slurp file))))

      (spit-compiled-file [_ ns-sym content]
        (doto (io/file compile-path (ns-sym->file-name ns-sym compiled-file-type))
          io/make-parents
          (spit content))))))

(defn read-ns-content [ns {:keys [io]}]
  (let [content (or (slurp-source-file io ns)
                    (throw (ex-info "Error reading NS" {:ns ns})))
        [ns-form & more-forms] (or (seq (reader/read-forms content))
                                   (throw (ex-info "Error reading forms in NS" {:ns ns})))
        {:keys [aliases refers] :as ns-header} (analyser/analyse-ns-form ns-form)]

    {:deps (into #{} (concat (vals aliases)
                             (keys refers)))
     :content {:ns-header ns-header
               :forms more-forms}}))

(defn transitive-read-forms [entry-ns-syms {:keys [io]}]
  (loop [[ns :as dep-queue] (vec entry-ns-syms)
         chains (into {} (map (juxt identity vector) entry-ns-syms))
         ns-order []
         ns-content {}]
    (cond
      (nil? ns) (for [ns ns-order]
                  (merge {:ns ns} (get ns-content ns)))

      (contains? ns-content ns) (recur (subvec dep-queue 1) chains (cons ns ns-order) ns-content)

      :else (let [{:keys [deps content]} (read-ns-content ns {:io io})
                  chain (get chains ns)]
              (if-let [cycle-ns (some (set chain) deps)]
                (throw (ex-info "cycle" {:cycle (reverse (cons cycle-ns chain))}))

                (recur (conj (into (subvec dep-queue 1)
                                   (remove (some-fn chains ns-content))
                                   deps)
                             ns)

                       (into chains (map (fn [dep-ns] [dep-ns (cons dep-ns chain)]) deps))

                       ns-order
                       (assoc ns-content ns content)))))))

(defn load-ns [entry-ns {:keys [io env]}]
  (let [ns-order (transitive-read-forms [entry-ns] {:io io, :env env})]
    (-> (reduce (fn [env {:keys [ns ns-header forms]}]
                  (reduce (fn [env form]
                            (let [{:keys [global-env]} (-> form
                                                           (analyser/analyse env)
                                                           (interpreter/interpret env))]
                              (merge env {:global-env global-env})))

                          (-> env
                              (assoc :current-ns ns)
                              (assoc-in [:global-env ns] ns-header))

                          forms))
                env
                ns-order)

        :global-env)))

(defn compile-ns [{:keys [ns ns-header forms]} env]
  (reduce (fn [{:keys [codes env]} form]
            (let [{:keys [global-env code]} (-> form
                                                (analyser/analyse env)
                                                (emitter/emit-expr env))]
              {:env (merge env {:global-env global-env})
               :codes (conj codes code)}))

          {:env (-> env
                    (assoc :current-ns ns)
                    (assoc-in [:global-env ns] ns-header))
           :codes []}

          forms))

(defn compile! [entry-ns {:keys [io env]}]
  (let [ns-order (transitive-read-forms [entry-ns] {:io io, :env env})]
    (reduce (fn [env {:keys [ns ns-header] :as ns-content}]
              (let [{:keys [env codes]} (compile-ns ns-content env)]
                (spit-compiled-file io ns (emitter/emit-ns {:codes codes, :ns ns, :ns-header ns-header}))
                env))
            env
            ns-order)))

(comment
  (do
    (defn fake-file [& forms]
      (->> forms
           (map prn-str)
           s/join))

    (defn fake-io [{:keys [source-files compiled-files]}]
      (let [!compiled-files (atom compiled-files)]
        {:!compiled-files !compiled-files
         :compiler-io (reify CompilerIO
                        (slurp-source-file [_ ns-sym]
                          (get source-files ns-sym))

                        (slurp-compiled-file [_ ns-sym]
                          (get @!compiled-files ns-sym))

                        (spit-compiled-file [_ ns-sym content]
                          (swap! !compiled-files assoc ns-sym content)))}))

    (let [fake-source-files {'bridje.foo (fake-file '(ns bridje.foo)
                                                    '(def (flip x y)
                                                       [y x]))
                             'bridje.bar (fake-file '(ns bridje.bar
                                                       {aliases {foo bridje.foo}})

                                                    '(def flipped
                                                       (foo/flip "Hello" "World")))}
          {:keys [compiler-io !compiled-files]} (fake-io {:source-files fake-source-files})]
      (compile! 'bridje.bar {:io compiler-io})

      (doseq [[ns content] @!compiled-files]
        (spit (doto (io/file "bridje-stuff/node"
                             (-> (name ns)
                                 (s/split #"\.")
                                 (->> (s/join "/"))
                                 (str ".js")))
                (io/make-parents))
              content)))))

(comment
  (interpret "(if true [{foo \"bar\", baz true} #{\"Hello\" \"world!\"}] false)"
             {:current-ns 'bridje.foo})

  (interpret "(def foo [\"Hello\" \"World\"])"
             {:current-ns 'bridje.foo})

  (interpret "(let [x \"Hello\", y \"World\"] [y x])"
             {:current-ns 'bridje.foo})

  (interpret "(fn [x] [x x])"
             {:current-ns 'bridje.foo})

  (-> (interpret "(defdata Nothing)"
                 {:current-ns 'bridje.foo})
      (get-in [:global-env 'bridje.foo :vars 'Nothing]))

  (interpret "(defdata (Just a)) (->Just \"Hello\")"
             {:current-ns 'bridje.foo}))
