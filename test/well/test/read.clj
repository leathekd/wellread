(ns well.test.read
  (:use [clojure.java.io :only [resource]]
        [clojure.string :only [trim-newline]]
        [clojure.test]
        [well.read]))

(defn credentials-available? []
  (when-let [creds (resource "test_credentials")]
    (.getPath creds)))

(def client (make-client))
(def test-skipped
  (str
   "Credentials file not found. In order to run the tests you need to create\n"
   "an Instapaper account and then create a file named 'test_credentials' in\n"
   "wellread/test with your username on the first line and password on the\n"
   "second.\n\n"
   "NOTE: Stories will be added and archived, so it's best if you don't use\n"
   "your real account."))

(use-fixtures :once
              (fn [f]
                (if-let [creds-path (credentials-available?)]
                  (f)
                  (println test-skipped)))
              (fn [f]
                (let [[username password] (read-args-file
                                           (credentials-available?))]
                  (login client username password))
                (f)))

(defn archive-all [client]
  (let [page (get-page client article-page-url)
        archive-all (first (.getByXPath page
                                        "//input[@value='Archive All']"))]
    (click archive-all)))

(defn populate-stories [client limit]
  (let [page (get-page client "http://www.instapaper.com/browse")
        read-later-links (.getByXPath page
                                      "//a[contains(@class, 'bookmarklet')]")]
    (doseq [link (take limit read-later-links)]
      (click link))))

(defn configure-kindle [client username]
  (let [page (get-page client kindle-page-url)]
    (.setValueAttribute (get-element page "kindle_email_address") username)
    (click (get-element page "submit"))))

(defn trimmed-output [f]
  (trim-newline (with-out-str (f))))

(deftest test-main
  (let [test-path (.getPath (resource "testfile"))
        go-read "Done. Time to start reading!"
        no-read "There aren't any unread articles. Bummer."
        permutations [-main
                      #(-main "user")
                      #(-main "user" "pass")
                      #(-main "-f" test-path)]]
    (with-redefs [send-and-archive (fn [& _] true)]
      (let [[no-args one-arg two-args file-arg]
            (map trimmed-output permutations)]
        (is (= usage no-args))
        (is (= usage one-arg))
        (is (= go-read two-args file-arg))))

    (with-redefs [send-and-archive (fn [& _])]
      (let [[no-args one-arg two-args file-arg]
            (map trimmed-output permutations)]
        (is (= usage no-args))
        (is (= usage one-arg))
        (is (= no-read two-args file-arg))))))

(deftest test-login
  (let [[username password] (read-args-file (credentials-available?))
        c (make-client)
        err (atom false)]
    (is (thrown? Exception (login c username "foo")))
    (try
      (login c username password)
      (catch Exception e
        (swap! err not))
      (finally
       (is (not @err))))))

(deftest test-archive-links
  (let [[username password] (read-args-file (credentials-available?))]
    (archive-all client)
    (is (= 0 (count (archive-links client))))

    (populate-stories client 5)
    (is (= 5 (count (archive-links client))))

    (archive client (archive-links client))
    (is (= 0 (count (archive-links client))))))

(deftest test-archive-chunk-count
  (let [[username password] (read-args-file (credentials-available?))]
    (is (= 10 (archive-chunk-count client)))))

(deftest test-send-to-kindle
  (let [[username password] (read-args-file (credentials-available?))]
    (configure-kindle client username)
    (with-redefs [click identity]
      (is (= "submit_send_now" (.getId (send-to-kindle client)))))))

(deftest test-send-and-archive
  (let [[username password] (read-args-file (credentials-available?))
        chunk-counts (atom [])
        sent (atom false)
        old-archive archive]
    (login client username password)
    (populate-stories client 5)
    (with-redefs [send-to-kindle (fn [_]
                                   (reset! sent (constantly true)))
                  archive (fn [client links]
                            (reset! chunk-counts (conj @chunk-counts
                                                       (count links)))
                            (old-archive client links))
                  archive-chunk-count (constantly 2)]
      (send-and-archive username password)
      (is @sent)
      (is (= 0 (count (archive-links client))))
      (is (= [2 2 1] @chunk-counts)))))
