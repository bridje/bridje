(ns bridje.compiler
  (:require [bridje.reader :as reader]
            [bridje.analyser :as analyser]
            [bridje.interpreter :as interpreter]
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

(defn transitive-read-forms [entry-ns-syms {:keys [io env]}]
  (loop [[[ns-sym :as dep-chain] & more-dep-chains] (map list entry-ns-syms)
         ns-order []
         ns-content {}]
    (if-not ns-sym
      {:ns-order ns-order
       :ns-content ns-content}

      (when-let [content (slurp-source-file io ns-sym)]
        (when-let [[ns-form & more-forms] (seq (reader/read-forms content))]
          (let [{:keys [aliases] :as ns-header} (analyser/analyse-ns-form ns-form env)]
            (recur (concat more-dep-chains (let [dep-chain-set (set dep-chain)]
                                             (for [dep-ns-sym (into #{} (remove (some-fn ns-content (into #{} (map first) (rest dep-chain)))) (vals aliases))]
                                               (let [dep-chain (cons dep-ns-sym dep-chain)]
                                                 (if-not (contains? dep-chain-set dep-ns-sym)
                                                   dep-chain
                                                   (throw (ex-info "uh oh - cycle" {:cycle (reverse dep-chain)})))))))
                   (cons ns-sym ns-order)
                   (assoc ns-content ns-sym {:ns-header ns-header
                                             :forms more-forms}))))))))

(defn load-ns [entry-ns {:keys [io env]}]
  (let [{:keys [ns-order ns-content]} (transitive-read-forms [entry-ns] {:io io, :env env})]
    (-> (reduce (fn [env current-ns]
                  (let [{:keys [ns-header forms]} (get ns-content current-ns)]
                    (reduce (fn [env form]
                              (let [{:keys [global-env]} (-> form
                                                             (analyser/analyse env)
                                                             (interpreter/interpret env))]
                                (merge env {:global-env global-env})))

                            (-> env
                                (assoc :current-ns current-ns)
                                (assoc-in [:global-env current-ns] ns-header))

                            forms)))
                env
                ns-order)

        :global-env)))

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

    (with-redefs [analyser/analyse-ns-form (fn [ns-form env]
                                             (case (->> ns-form :forms second :sym)
                                               bridje.foo '{}
                                               bridje.bar '{:aliases {foo bridje.foo}}))]
      (let [fake-source-files {'bridje.foo (fake-file '(ns bridje.foo) '(defdata (Just value)))
                               'bridje.bar (fake-file '(ns bridje.bar) '(def just (foo/->Just "Hello")))}
            {:keys [compiler-io !compiled-files]} (fake-io {:source-files fake-source-files})]
        (load-ns 'bridje.bar
                 {:io compiler-io})))))

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
