(ns well.read
  (:use [clojure.string :only [split trim]]
        [clojure.tools.cli :only [cli]])
  (:import (com.gargoylesoftware.htmlunit BrowserVersion
                                          ElementNotFoundException WebClient)
           (com.gargoylesoftware.htmlunit.html HtmlElement HtmlPage)
           (java.net URL)
           (org.apache.commons.logging LogFactory))
  (:gen-class))

(def login-page-url "http://www.instapaper.com/user/login")
(def article-page-url "http://www.instapaper.com/u")
(def account-page-url "http://www.instapaper.com/user")
(def kindle-page-url "http://www.instapaper.com/user/kindle")

(defn page-url [page]
  (.toString (.getUrl page)))

(defn get-page [client url]
  (if-let [page (.getPage client url)]
    (if (re-find (re-pattern url) (page-url page))
      page
      (throw (Exception. (format "Failed to get %s got %s instead"
                                 url (page-url page)))))
    (throw (Exception. (str "Failed to get " url)))))

(defn get-element [page id]
  (when page
    (if-let [elt (.getElementById page id)]
      elt
      (throw (Exception.
              (format "Failed to get the element with the id %s on the page %s"
                      id page))))))

(defn get-enclosing-form [elt]
  (when elt
    (if-let [form (.getEnclosingForm elt)]
      form
      (throw (Exception.
              (format "The element with the id %s isn't inside a form"
                      (.getId elt)))))))

(defn get-input-by-name [form name]
  (when form
    (try
      (.getInputByName form name)
      (catch ElementNotFoundException e
        (throw (Exception. (str "Couldn't find the input with the name"
                                name)))))))

(defn click [elt]
  (when elt
    (.click elt)))

(defn login [client username password]
  (let [page (get-page client login-page-url)
        button (get-element page "log_in")
        form (get-enclosing-form button)
        username-input (get-input-by-name form "username")
        password-input (get-input-by-name form "password")]
    (when (and button username-input password-input)
      (.setValueAttribute username-input username)
      (.setValueAttribute password-input password)
      (let [new-page (click button)]
        (when-not (= article-page-url (page-url new-page))
          (throw (Exception. "Login failed.")))))))

(defn archive-links [client]
  (let [articles
        (-> client
            (get-page article-page-url)
            (.getByXPath (str "//div[@id='bookmark_list']"
                              "//a[contains(@class,'archiveButton')]")))]
    (seq articles)))

(defn send-to-kindle [client]
  (-> client
      (get-page account-page-url)
      (get-element "submit_send_now")
      (click)))

(defn subscriber? [client]
  (-> client
      (.getPage account-page-url)
      (.getBody)
      (.getTextContent)
      (.indexOf "You are a Subscriber. Thank you for your support.")
      pos?))

(defn archive-chunk-count [client]
  (if-not (subscriber? client)
    10
    (-> client
        (get-page kindle-page-url)
        (get-element "user_kindle_article_limit")
        (.getSelectedOptions)
        first
        (.getValueAttribute)
        (Integer.))))

(defn archive [client links]
  (get-page client article-page-url)
  (doseq [link links]
    (click link)))

(defn make-client []
  (doto (WebClient.)
    (.setJavaScriptEnabled false)))

(defn send-and-archive [username password]
  (let [client (make-client)]
    (login client username password)
    (when-let [links (archive-links client)]
      (doseq [chunk (partition-all (archive-chunk-count client)
                                   links)]
        (send-to-kindle client)
        (archive client chunk))
      true)))

(defn read-args-file [path]
  (let [[u p] (split (slurp path) #"\n")]
    (if (and u p)
      [u p]
      (throw (IllegalArgumentException.
              (str "Didn't find a username and password in the file " path))))))

(defn parse-chunk-size [size]
  (when size
    (try
      (Integer. size)
      (catch Exception e
        nil))))

(def usage (str "Arguments:\n"
               "  <username> : the Instapaper username\n"
               "  <password> : the Instapaper password\n"
               "  -f, --file <file path> : Path to a file containing two\n"
               "    lines. Username on the first, password on the second."))

(defn -main [& args]
  (try
    (let [[opts args] (cli args ["-f" "--file" ""])
          [username password] (map trim (if (:file opts)
                                          (read-args-file (:file opts))
                                          args))]
      (if (and username password)
        (if (send-and-archive username password)
          (println "Done. Time to start reading!")
          (println "There aren't any unread articles. Bummer."))
        (println usage)))
    #_(catch Exception e
      (println "Something seems to have gone wrong.\n" (.getMessage e)))))
