all:
	mvn clean install -DskipTests
	cat res/stub.sh target/janusbench-0.1.0-jar-with-dependencies.jar > target/janusbench && chmod +x target/janusbench

run:
	java -jar target/janusbench-0.1.0-jar-with-dependencies.jar run -s $(storage) $(if $(index), -i $(index), )

test:
	mvn clean test

clean:
	mvn clean

doc:
	mkdir -p javadoc
	javadoc -d javadoc -sourcepath src/main/java -subpackages de
