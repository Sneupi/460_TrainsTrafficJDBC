SHELL:=/bin/bash
-include .env
export CLASSPATH := .:${DB_DRIVER}

.PHONY: clean scrub prog3 example backup fetch login

# clean local files
clean: 
	rm -f *.db
	rm -f *.class

# ensure files ready to insert
scrub: highway*.csv clean
	javac Scrubber.java
	java Scrubber highway*.csv

# run prog3 assignment
prog3: clean
	javac Prog3.java
	java Prog3 ${DB_CLASSNAME} $(DB_URL)

# JDBC example
example:
	javac JDBC.java
	java JDBC $(DB_USERNAME) $(DB_PASSWORD)

# backup proj to SSH
backup: clean
	scp -r * ${SSH}:${SSH_PATH}

# fetch files from SSH
fetch: clean
	scp -r ${SSH}:${SSH_PATH}/* .

# login to SSH
login:
	ssh ${SSH}
