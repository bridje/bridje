(ns bridje.main
  (:require [bridje.compiler :refer [compile!]]
            [bridje.file-io :as file-io]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.set :as set]
            [clojure.tools.cli :as cli]))

(defn run-main [{:keys [main-ns args]} {:keys [io]}]
  (let [cbs (loop [to-load #{main-ns}
                   cbs []
                   loaded #{}]
              (if (empty? to-load)
                cbs
                (let [results (map (fn [ns-sym]
                                     (file-io/slurp-compiled-file io ns-sym :clj))
                                   to-load)]
                  (recur (set/difference (into #{} (mapcat :deps) results) loaded)
                         (into (into [] (map (comp eval :cb)) results) cbs)
                         (set/union loaded to-load)))))

        env (reduce (fn [env cb]
                      (cb env))
                    {}
                    cbs)]

    (when-let [main-fn (get-in env [main-ns :vars 'main :value])]
      (main-fn args))))

(comment
  (let [fake-source-files {'bridje.foo (bridje.compiler/fake-file
                                        '(ns bridje.foo)

                                        '(def (flip x y)
                                           [y x])

                                        '(defdata Nothing)
                                        '(defdata (Just a)))

                           'bridje.bar (bridje.compiler/fake-file
                                        '(ns bridje.bar
                                           {aliases {foo bridje.foo}})

                                        '(def (main args)
                                           (let [seq ["ohno"]
                                                 just (foo/->Just "just")
                                                 nothing foo/Nothing]
                                             {message (foo/flip "World" "Hello")
                                              seq seq
                                              the-nothing nothing
                                              just just
                                              justtype (match just
                                                              foo/Just "it's a just"
                                                              foo/Nothing "it's nothing"
                                                              "it's something else")
                                              justval (foo/Just->a just)})))}

        {:keys [compiler-io !compiled-files]} (bridje.compiler/fake-io {:source-files fake-source-files})]

    (bridje.compiler/compile! 'bridje.bar {:io compiler-io, :env {}})

    (run-main {:main-ns 'bridje.bar}
              {:io compiler-io})))

(defn -main [& args]
  (let [{[cmd & params] :arguments,
         {:keys [source-paths]} :options
         :as cli-opts} (cli/parse-opts args
                                       [["-p" "--source-paths PATHS" "Source paths"
                                         :parse-fn (comp #(map io/file %) #(s/split % #":"))]])

        io (file-io/->io {:source-paths (or source-paths
                                            [(io/file ".")])})]

    (case (keyword cmd)
      :compile (compile! (first params) {:env {},
                                         :io io})

      :run (run-main {:main-ns (symbol (first args))
                      :args (rest args)}
                     io))))
