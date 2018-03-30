(ns bridje.runtime)

(defrecord ADT [adt-type params])

(def ^:dynamic *effect-fns* {})
