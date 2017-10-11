(ns bridje.main
  (:require [bridje.compiler :refer [compile! ->io]]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.tools.cli :as cli]))

(defn -main [& args]
  (let [{[cmd & params] :arguments,
         {:keys [source-paths]} :options
         :as cli-opts} (cli/parse-opts args
                                       [["-p" "--source-paths PATHS" "Source paths"
                                         :parse-fn (comp #(map io/file %) #(s/split % #":"))]])]
    (case (keyword cmd)
      :compile (compile! (first params) {:env {},
                                         :io (->io {:source-paths (or source-paths
                                                                      [(io/file ".")])})}))))
