FROM flink:1.9.3-scala_2.12

WORKDIR /opt/flink/bin

COPY target/scala-2.12/made-simple-bant-assembly-0.1-SNAPSHOT.jar /opt/made-simple-bant-assembly-0.1-SNAPSHOT.jar
