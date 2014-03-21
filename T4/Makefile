.PHONY: all
all:
	javac *.java
	rmic RegistryProxyImpl

.PHONY: serve
serve:
	java StartRegistry 3360

.PHONY: run
run:
	java ServerImpl localhost:3360

.PHONY: clean
clean:
	rm -f *.class
