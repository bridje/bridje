(ns bridje.quoter
  (:require [bridje.analyser :as analyser]
            [bridje.util :as u]))

(defn sym-form [sym]
  {:form-type :symbol
   :sym (name sym)})

(defn quote-mk-form [form-type]
  (let [sym (symbol (str "->" (name (u/form-adt-kw form-type))))]
    {:form-type :namespaced-symbol
     :ns 'bridje.forms
     :sym sym}))

(defn expand-syntax-quotes [form {:keys [current-ns] :as env}]
  (letfn [(syntax-quote-form [{:keys [form-type forms] :as form} {:keys [splice?]}]
            (let [quoted-form (case form-type
                                :syntax-quote (-> (:form form)
                                                  (syntax-quote-form {:env env})
                                                  (syntax-quote-form {:env env}))

                                :unquote (:form form)

                                :unquote-splicing (if splice?
                                                    (:form form)
                                                    (throw (ex-info "unquote-splicing used outside of collection" {:form form})))

                                :quote {:form-type :quote
                                        :form (syntax-quote-form (:form form) {:env env})}

                                {:form-type :list
                                 :forms [(quote-mk-form form-type)
                                         {:form-type :record
                                          :forms (case form-type
                                                   :string [(sym-form 'string) form]
                                                   :bool [(sym-form 'bool) form]

                                                   (:int :float :big-int :big-float) [(sym-form 'number) form]

                                                   (:symbol :namespaced-symbol) (let [{:keys [ns sym]} (or (analyser/env-resolve form :vars env)
                                                                                                           {:ns (or (:ns form) current-ns),
                                                                                                            :sym (:sym form)})]
                                                                                  [(sym-form 'ns) {:form-type :quote,
                                                                                                   :form (sym-form ns)}
                                                                                   (sym-form 'sym) {:form-type :quote,
                                                                                                    :form (sym-form sym)}])

                                                   (:list :vector :set :record) [(sym-form 'forms) (let [splice? (some #(= :unquote-splicing (:form-type %)) forms)
                                                                                                         inner-forms {:form-type :vector
                                                                                                                      :forms (mapv #(syntax-quote-form % {:splice? splice?}) forms)}]
                                                                                                     (if splice?
                                                                                                       {:form-type :list
                                                                                                        :forms [{:form-type :namespaced-symbol
                                                                                                                 :ns 'bridje.forms
                                                                                                                 :sym 'concat}
                                                                                                                inner-forms]}

                                                                                                       inner-forms))])}]})]
              (if (and splice? (not= form-type :unquote-splicing))
                {:form-type :vector
                 :forms [quoted-form]}

                quoted-form)))

          (expand-sq* [{:keys [form-type] :as form}]
            (case form-type
              (:string :bool :int :big-int :float :big-float :symbol :namespaced-symbol) form
              (:vector :set :list :record) (update form :forms #(mapv expand-sq* %))
              :quote (update form :form expand-sq*)

              :syntax-quote (syntax-quote-form (:form form) {:env env, :quote-type :syntax-quote})
              :unquote (throw (ex-info "'unquote' outside of 'syntax-quote'" {:form form}))
              :unquote-splicing (throw (ex-info "'unquote-splicing' outside of 'syntax-quote'" {:form form}))))]

    (expand-sq* form)))

(comment
  (let [env {:global-env {'bridje.forms {:vars {'->VectorForm {:value {}}
                                                '->IntForm {:value {}}
                                                '->ListForm {:value {}}
                                                '->StringForm {:value {}}
                                                '->RecordForm {:value {}}
                                                '->SymbolForm {:value {}}
                                                '->NamespacedSymbolForm {:value {}}}}}}]
    (-> (expand-normal-quotes (first (bridje.reader/read-forms "['[1 '''1]]")))
        (analyser/analyse env)
        (bridje.emitter/emit-value-expr env))))

(let [env {:current-ns 'bridje.foo
           :global-env {'bridje.forms {:vars {'->VectorForm {:value {}}
                                              '->IntForm {:value {}}
                                              '->ListForm {:value {}}
                                              '->QuotedForm {:value {}}
                                              '->StringForm {:value {}}
                                              '->RecordForm {:value {}}
                                              '->SymbolForm {:value {}}
                                              '->NamespacedSymbolForm {:value {}}}}}}]
  (-> (first (bridje.reader/read-forms "`foo"))
      (expand-syntax-quotes env)
      (analyser/analyse env)
      (bridje.emitter/emit-value-expr env)))

(comment
  (-> (first (bridje.reader/read-forms "`[1 ~@['2 '3 '4]]"))
      (expand-syntax-quotes {})))
