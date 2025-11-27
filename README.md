# Concurrent API Aggregator Service

A high-performance, non-blocking Netty server that aggregates data from public APIs.

## Prerequisites
- Java 21+
- Maven
- Redis (running on localhost:6379)

## Build
To build the project into a single executable JAR using Maven:
```bash
mvn clean package
```

## Running
To run the application use:
```
java -jar target/netty-aggregator-1.0-SNAPSHOT.jar 
```

## Testing
To get a response use:
```
curl http://localhost:8080/api/dashboard
```