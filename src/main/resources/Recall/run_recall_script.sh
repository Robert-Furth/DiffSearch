mvn exec:java -Dexec.mainClass=research.diffsearch.main.App -Dexec.args="-fe -r -k 1000 -silent -lang java -vl 300 -extractors node;triangle;rulecount:1400 -mt 10" -e
mvn exec:java -Dexec.mainClass=research.diffsearch.main.App -Dexec.args="-b -r -k 1000 -silent -lang java -vl 300 -extractors node;triangle;rulecount:1400 -mt 10" -e