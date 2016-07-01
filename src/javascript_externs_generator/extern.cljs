(ns javascript-externs-generator.extern
  (:require [goog.dom :as dom]
            [goog.object :as obj]
            [clojure.string :as string]))

(defn prop-type
  "Differentiate between function property and object property"
  [obj]
  (if (fn? obj)
    :function
    :object))

(defn parent-type?
  "Whether it is possible for this object to have child properties"
  [obj]
  (or (= (goog/typeOf obj) "object")
      (fn? obj)))

(defn generate-object-tree
  "Build a recursive tree representation of the JavaScript object
  and metadata about its properties, prototype, and functions"
  [obj name]
  {:name      name
   :type      (prop-type obj)
   :props     (for [prop-name (js-keys obj)
                    :let [child (obj/get obj prop-name)]]
                (if (and (parent-type? child)
                         (not (identical? obj child)))
                  (generate-object-tree child prop-name)
                  {:name prop-name :type (prop-type child)}))
   :prototype (for [prop-name (js-keys (.-prototype obj))]
                {:name prop-name :type :function})})

(defn emit-props-extern
  "Return recursive string representation of an object's properties"
  [obj]
  (let [{:keys [name type props]} obj
        function-str (if (and (= type :function)
                              (empty? props)) "function()" "")
        props-extern (str "{" (string/join "," (for [p props]
                                                 (emit-props-extern p))) "}")]
    (if (get obj :root)
      (str "var " name " = " function-str props-extern ";")
      (str (quote-string name) ": " function-str props-extern))))


(defn emit-prototype
  "Return string representation of an object's prototype/functions"
  [namespace prototype]
  (when (seq prototype)
    (str namespace ".prototype = {" (string/join "," (for [p prototype]
                                                       (str (quote-string (:name p)) ": function(){}"))) "};")))

(defn emit-prototype-extern
  "Return recursive string representation of an object's prototype chain"
  [obj namespace]
  (let [{:keys [name props prototype]} obj
        prototype-extern (emit-prototype namespace prototype)
        child-prototype-externs (for [p props]
                                  (emit-prototype-extern p (str namespace "." (:name p))))]
    (str prototype-extern (string/join child-prototype-externs))))

(defn extract
  "Recursively extract properties and prototypes from a JavaScript object
  into an extern for use with the Google Closure Compiler"
  [name js-object]
  (let [tree (assoc (generate-object-tree js-object name) :root true)
        props-extern (emit-props-extern tree)
        prototype-extern (emit-prototype-extern tree name)]
    (str props-extern prototype-extern)))

(defn extract-loaded
  "Grab a loaded JavaScript object to extract an extern"
  [name]
  (let [sandbox (dom/getElement "sandbox")
        js-object (-> sandbox (obj/get "contentWindow") (obj/get name))]
    (when (nil? js-object)
      (throw (str "Namespace '" name "' was not found. Make sure the library is loaded and the name is spelled correctly.")))
    (extract name js-object)))


