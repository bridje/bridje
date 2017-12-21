(ns bridje.reader
  (:require [instaparse.core :as i]
            [clojure.string :as str]
            [clojure.walk :as w]))

(i/defparser sexp-parser
  "<Forms> = <whitespace>* (Form & delimiter <whitespace>*)*
   <Form> = num | string | symbol | keyword | coll

   <num> = int | float | big-int | big-float
   int = #'-?\\d+'
   float = #'-?\\d+\\.\\d+'
   big-int = #'-?\\d+' <'N'>
   big-float = #'-?\\d+(\\.\\d+)?' <'M'>

   string = <'\"'> (string-part | string-escape)* <'\"'>
   string-part = #'[^\\\\\"]+'
   string-escape = <'\\\\'> #'[\"\\\\nrt]'

   keyword = <':'> symbol
   symbol = !num #'[^\"#:()\\[\\]{}\\s,]+'

   <coll> = list | vector | set | record
   list = <'('> Forms <')'>
   vector = <'['> Forms <']'>
   set = <'#{'> Forms <'}'>
   record = <'{'> Forms <'}'>

   <whitespace> = #'[\\s,]'
   <delimiter> = whitespace | #'[\\[\\](){}#]' | #'$'"

  :output-format :enlive)

(def transformations
  {:keyword (fn [{:keys [sym]}] {:tag :keyword, :kw (keyword sym)})
   :symbol (fn [sym] {:tag :symbol, :sym (symbol sym)})
   :string (fn [& parts]
             {:tag :string,
              :string (->> (for [{:keys [tag], [part] :content} parts]
                             (case tag
                               :string-part part
                               :string-escape (case (first part)
                                                \\ "\\"
                                                \n "\n"
                                                \t "\t"
                                                \r "\r"
                                                \" "\"")))
                           str/join)})

   :int (fn [i] {:tag :int, :number (Long/parseLong i)})
   :float (fn [f] {:tag :float, :number (Double/parseDouble f)})
   :big-int (fn [i] {:tag :big-int, :number (BigInteger. i)})
   :big-float (fn [f] {:tag :big-float, :number (BigDecimal. f)})})

(defn read-forms [s]
  (->> (sexp-parser s)
       (i/transform transformations)
       (i/add-line-and-column-info-to-metadata s)
       (w/postwalk (some-fn {:tag :form-type, :content :forms} identity))))
