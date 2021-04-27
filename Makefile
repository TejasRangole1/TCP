all: TCPend.class

TCPend.class: src/TCPend.java src/Sender.java src/Receiver.java src/Segment.java src/Utility.java
	javac $^

clean:
	$(RM) src/*.class
