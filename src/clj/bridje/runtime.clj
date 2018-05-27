(ns bridje.runtime
  (:require [clojure.walk :as walk]
            [clojure.string :as str]))

(defn- find-syms [form]
  (cond
    (symbol? form) #{form}
    (sequential? form) (into #{} (mapcat find-syms) form)))

(defn drop-dot [sym]
  (symbol (subs (name sym) 0 (dec (count (name sym))))))

(defmacro ->brj [env & body]
  (let [env-sym (gensym 'env)
        adt-syms (into {}
                       (comp (filter (comp #{\.} last name))
                             (map (juxt identity drop-dot)))
                       (find-syms body))
        local-mapping (into {} (map (juxt key (comp gensym name val))) adt-syms)]
    `(let [~env-sym ~env
           ~@(->> adt-syms
                  (mapcat (fn [[form-sym adt-sym]]
                            [(get local-mapping form-sym) `(get-in ~env-sym [:vars '~adt-sym :value])])))]
       ~@(walk/postwalk (some-fn local-mapping identity) body))))

(defmacro defbrj [sym & body]
  `(defn ~sym [env#]
     (->brj env# ~@body)))

(defbrj concat
  (fn [vecs]
    (into [] (mapcat identity vecs))))
