(ns bridje.compiler
  (:require [bridje.reader :as reader]
            [bridje.analyser :as analyser]
            [bridje.emitter :as emitter]
            [clojure.java.io :as io]
            [clojure.string :as s]))

(defprotocol CompilerIO
  (slurp-source-file [_ ns-sym])
  (slurp-compiled-file [_ ns-sym file-type])
  (spit-compiled-file [_ ns-sym file-type content]))

(defn real-io [{:keys [source-paths compile-path]}]
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

      (slurp-compiled-file [_ ns-sym file-type]
        (let [file (io/file compile-path (ns-sym->file-name ns-sym file-type))]
          (when (.exists file)
            (slurp file))))

      (spit-compiled-file [_ ns-sym file-type content]
        (doto (io/file compile-path (ns-sym->file-name ns-sym file-type))
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
                (spit-compiled-file io ns :clj (emitter/emit-ns {:codes codes, :ns ns, :ns-header ns-header}))
                env))
            env
            ns-order)))

(defn ->io [{:keys [source-paths]}]
  (let [compile-path (io/file "bridje-stuff" "node")
        ->file-path (fn [ns-sym file-type]
                      (str (-> (name ns-sym)
                               (s/split #"\.")
                               (->> (s/join "/")))
                           "."
                           (name file-type)))

        ->compiled-file (fn [ns-sym file-type]
                          (io/file compile-path (->file-path ns-sym file-type)))]

    (reify CompilerIO
      (slurp-source-file [_ ns-sym]
        (when-let [source-file (some #(when (.exists %) %) (map #(io/file % (->file-path ns-sym :brj)) source-paths))]
          (slurp source-file)))

      (slurp-compiled-file [_ ns-sym file-type]
        (let [compiled-file (->compiled-file ns-sym file-type)]
          (when (.exists compiled-file)
            (slurp compiled-file))))

      (spit-compiled-file [_ ns-sym file-type content]
        (spit (doto (->compiled-file ns-sym file-type)
                (io/make-parents))
              content)))))

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

                        (slurp-compiled-file [_ ns-sym file-type]
                          (get @!compiled-files [ns-sym file-type]))

                        (spit-compiled-file [_ ns-sym file-type content]
                          (swap! !compiled-files assoc [ns-sym file-type] content)))}))

    (let [fake-source-files {'bridje.foo (fake-file '(ns bridje.foo)

                                                    '(def (flip x y)
                                                       [y x])

                                                    '(defdata Nothing)
                                                    '(defdata (Just a)))

                             'bridje.bar (fake-file '(ns bridje.bar
                                                       {aliases {foo bridje.foo}})

                                                    '(def (main args)
                                                       (let [seq ["ohno"]
                                                             just (foo/->Just "just")]
                                                         {message (foo/flip "World" "Hello")
                                                          seq seq
                                                          ;; is-empty (js/.isEmpty seq)
                                                          just just
                                                          justtype (match just
                                                                     foo/Just "it's a just"
                                                                     foo/Nothing "it's nothing"
                                                                     "it's something else")
                                                          justval (foo/Just->a just)})))}

          {:keys [compiler-io !compiled-files]} (fake-io {:source-files fake-source-files})]

      (compile! 'bridje.bar {:io compiler-io})

      (doseq [[[ns file-type] content] @!compiled-files]
        (spit (doto (io/file "bridje-stuff/jvm"
                             (-> (name ns)
                                 (s/split #"\.")
                                 (->> (s/join "/"))
                                 (str "." (name file-type))))
                (io/make-parents))
              content)))))
