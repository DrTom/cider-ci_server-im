{:persistence {
               :db {
                    :adapter "postgresql" 
                    :classname "org.postgresl.Driver"
                    :database "cider_ci_dev"
                    :username "thomas"
                    :password "thomas"
                    :pool 25
                    :subname "localhost:5432"
                    :subprotocol "postgresql"
                    }}
 :http_server { 
               :host "localhost"
               :port 8080
               :ssl false
               :tb_context "/cider-ci-dev" }
 :git {
        :repositories_path "/Users/thomas/Programming/CIDER-CI/tmp/repositories" }}
