mvn exec:java -Dexec.mainClass=research.diffsearch.main.App -Dexec.args="-oj -r -q \"System.out.println(EXPR);-->_\""
mvn exec:java -Dexec.mainClass=research.diffsearch.main.App -Dexec.args="-oj -r -q \"ID<0> * ID<1>;-->ID<0> + ID<1>;\""
mvn exec:java -Dexec.mainClass=research.diffsearch.main.App -Dexec.args="-oj -r -q \"EXPR = LT;-->EXPR = ID;\""
mvn exec:java -Dexec.mainClass=research.diffsearch.main.App -Dexec.args="-oj -r -q \"_-->import java.io.ID;\""
mvn exec:java -Dexec.mainClass=research.diffsearch.main.App -Dexec.args="-oj -r -q \"if (EXPR != null) {-->if (EXPR == null) {\""