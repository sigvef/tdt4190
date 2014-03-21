.PHONY: all
all:
	javac *.java
	rmic RegistryProxyImpl

.PHONY: serve
serve:
	java StartRegistry 3360

.PHONY: run
run:
	java ServerImpl localhost:3360 false

.PHONY: run-probe
run-probe:
	java ServerImpl localhost:3360 true

.PHONY: clean
clean:
	rm -f *.class
