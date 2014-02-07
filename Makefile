.PHONY: all
all:
	javac *.java
	rmic PeerConnection

.PHONY: run
run:
	java Game

.PHONY: clean
clean:
	rm -f *.class
