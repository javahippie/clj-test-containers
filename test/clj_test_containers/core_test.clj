(ns clj-test-containers.core-test
  (:require
   [clj-test-containers.core :as sut]
   [clojure.test :refer [deftest is testing]])
  (:import
   (org.testcontainers.containers
    PostgreSQLContainer)))

(deftest create-test
  (testing "Testing basic testcontainer generic image initialisation"
    (let [container (sut/create {:image-name "postgres:12.2"
                                 :exposed-ports [5432]
                                 :env-vars {"POSTGRES_PASSWORD" "pw"}})
          initialized-container (sut/start! container)
          stopped-container (sut/stop! container)]
      (is (some? (:id initialized-container)))
      (is (some? (:mapped-ports initialized-container)))
      (is (some? (get (:mapped-ports initialized-container) 5432)))
      (is (nil? (:id stopped-container)))
      (is (nil? (:mapped-ports stopped-container)))))

  (testing "Testing basic testcontainer generic image initialisation with wait for log message"
    (let [container (sut/create {:image-name "postgres:12.2"
                                 :exposed-ports [5432]
                                 :env-vars {"POSTGRES_PASSWORD" "pw"}
                                 :wait-for {:wait-strategy :log :message "accept connections"}})
          initialized-container (sut/start! container)
          stopped-container (sut/stop! container)]
      (is (some? (:id initialized-container)))
      (is (some? (:mapped-ports initialized-container)))
      (is (some? (get (:mapped-ports initialized-container) 5432)))
      (is (= (:wait-for-log-message initialized-container) ".*accept connections.*\\n"))
      (is (nil? (:id stopped-container)))
      (is (nil? (:mapped-ports stopped-container)))))

  (testing "Testing basic testcontainer image creation from docker file"
    (let [container (sut/create-from-docker-file {:exposed-ports [80]
                                                  :docker-file "test/resources/Dockerfile"})
          initialized-container (sut/start! container)
          stopped-container (sut/stop! container)]
      (is (some? (:id initialized-container)))
      (is (some? (:mapped-ports initialized-container)))
      (is (some? (get (:mapped-ports initialized-container) 80)))
      (is (nil? (:id stopped-container)))
      (is (nil? (:mapped-ports stopped-container)))))


  (testing "Executing a command in the running Docker container with a custom container"
    (let [container (sut/init {:container (PostgreSQLContainer. "postgres:12.2")})
          initialized-container (sut/start! container)
          result (sut/execute-command! initialized-container ["whoami"])
          _stopped-container (sut/stop! container)]
      (is (= 0 (:exit-code result)))
      (is (= "root\n" (:stdout result))))))

(deftest execute-command-in-container

  (testing "Executing a command in the running Docker container"
    (let [container (sut/create {:image-name "postgres:12.2"
                                 :exposed-ports [5432]
                                 :env-vars {"POSTGRES_PASSWORD" "pw"}})
          initialized-container (sut/start! container)
          result (sut/execute-command! initialized-container ["whoami"])
          _stopped-container (sut/stop! container)]
      (is (= 0 (:exit-code result)))
      (is (= "root\n" (:stdout result))))))

(deftest init-volume-test

  (testing "Testing mapping of a classpath resource"
    (let [container (-> (sut/create {:image-name "postgres:12.2"
                                     :exposed-ports [5432]
                                     :env-vars {"POSTGRES_PASSWORD" "pw"}})
                        (sut/map-classpath-resource! {:resource-path "test.sql"
                                                      :container-path "/opt/test.sql"
                                                      :mode :read-only}))
          initialized-container (sut/start! container)
          file-check (sut/execute-command! initialized-container ["tail" "/opt/test.sql"])
          stopped-container (sut/stop! container)]
      (is (some? (:id initialized-container)))
      (is (some? (:mapped-ports initialized-container)))
      (is (some? (get (:mapped-ports initialized-container) 5432)))
      (is (= 0 (:exit-code file-check)))
      (is (nil? (:id stopped-container)))
      (is (nil? (:mapped-ports stopped-container)))))

  (testing "Testing mapping of a filesystem-binding"
    (let [container (-> (sut/create {:image-name "postgres:12.2"
                                     :exposed-ports [5432]
                                     :env-vars {"POSTGRES_PASSWORD" "pw"}})
                        (sut/bind-filesystem!  {:host-path "."
                                                :container-path "/opt"
                                                :mode :read-only}))
          initialized-container (sut/start! container)
          file-check (sut/execute-command! initialized-container ["tail" "/opt/README.md"])
          stopped-container (sut/stop! container)]
      (is (some? (:id initialized-container)))
      (is (some? (:mapped-ports initialized-container)))
      (is (some? (get (:mapped-ports initialized-container) 5432)))
      (is (= 0 (:exit-code file-check)))
      (is (nil? (:id stopped-container)))
      (is (nil? (:mapped-ports stopped-container)))))

  (testing "Copying a file from the host into the container"
    (let [container (-> (sut/create {:image-name "postgres:12.2"
                                     :exposed-ports [5432]
                                     :env-vars {"POSTGRES_PASSWORD" "pw"}})
                        (sut/copy-file-to-container!  {:path "test.sql"
                                                       :container-path "/opt/test.sql"
                                                       :type :host-path}))
          initialized-container (sut/start! container)
          file-check (sut/execute-command! initialized-container ["tail" "/opt/test.sql"])
          stopped-container (sut/stop! container)]
      (is (some? (:id initialized-container)))
      (is (some? (:mapped-ports initialized-container)))
      (is (some? (get (:mapped-ports initialized-container) 5432)))
      (is (= 0 (:exit-code file-check)))
      (is (nil? (:id stopped-container)))
      (is (nil? (:mapped-ports stopped-container)))))

  (testing "Copying a file from the classpath into the container"
    (let [container (-> (sut/create {:image-name "postgres:12.2"
                                     :exposed-ports [5432]
                                     :env-vars {"POSTGRES_PASSWORD" "pw"}})
                        (sut/copy-file-to-container!  {:path "test.sql"
                                                       :container-path "/opt/test.sql"
                                                       :type :classpath-resource}))
          initialized-container (sut/start! container)
          file-check (sut/execute-command! initialized-container ["tail" "/opt/test.sql"])
          stopped-container (sut/stop! container)]
      (is (some? (:id initialized-container)))
      (is (some? (:mapped-ports initialized-container)))
      (is (some? (get (:mapped-ports initialized-container) 5432)))
      (is (= 0 (:exit-code file-check)))
      (is (nil? (:id stopped-container)))
      (is (nil? (:mapped-ports stopped-container))))))

(deftest networking-test
  (testing "Putting two containers into the same network and check their communication"
    (let [network (sut/create-network)
          server-container (sut/create {:image-name "alpine:3.5"
                                        :network network
                                        :network-aliases ["foo"]
                                        :command ["/bin/sh"
                                                  "-c"
                                                  "while true ; do printf 'HTTP/1.1 200 OK\\n\\nyay' | nc -l -p 8080; done"]})
          client-container (sut/create {:image-name "alpine:3.5"
                                        :network network
                                        :command ["top"]})
          started-server (sut/start! server-container)
          started-client (sut/start! client-container)
          response (sut/execute-command! started-client ["wget", "-O", "-", "http://foo:8080"])
          _stopped-server (sut/stop! started-server)
          _stopped-client (sut/stop! started-client)]
      (is (= 0 (:exit-code response)))
      (is (= "yay" (:stdout response))))))
