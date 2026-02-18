# microservice
cd nfvo-simulator
mvn clean package -DskipTests
java -jar target/nfvo-simulator-1.0.0-SNAPSHOT.jar instantiate my-vnf 2 4
java -jar target/nfvo-simulator-1.0.0-SNAPSHOT.jar instantiate my-vnf 2 4 --requestId req-123 --delay
java -jar target/nfvo-simulator-1.0.0-SNAPSHOT.jar terminate <vnf-id> --requestId req-456
java -jar target/nfvo-simulator-1.0.0-SNAPSHOT.jar status <vnf-id>
java -jar target/nfvo-simulator-1.0.0-SNAPSHOT.jar list