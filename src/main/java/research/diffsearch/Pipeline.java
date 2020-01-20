package research.diffsearch;

import org.antlr.v4.runtime.tree.ParseTreeWalker;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Pipeline {

    /**
     * Extraction of the features from changes in the form: old -> new. The features vectors are written in the file
     * changes_feature_vectors.csv and the change in the string format in the file changes_strings.txt to have
     * an index of features vectors and changes.
     *
     * @param changes_list : list of the changes in a string format: old -> new
     * @return Number of changes
     */
    public static long feature_extraction(List<String> changes_list){
        long changes_number = 0;

        try {
            changes_number = changes_list.size();
            //Creation of a buffered writer for the features and the change in a string form (for print)
            BufferedWriter buff_writer_features = new BufferedWriter(new FileWriter("./src/main/resources/Features_Vectors/changes_feature_vectors.csv"));
            // Writing the string change in a file (ONLY FOR TESTING)
            FileWriter writer = new FileWriter("./src/main/resources/Features_Vectors/changes_strings.txt");
            BufferedWriter bw = new BufferedWriter(writer);

            //For each change: creation of AST, feature extraction and file write of the feature vector
            for (String change_string : changes_list) {

                Python3_Tree change =new Python3_Tree(change_string);
                if(change.error)
                    continue;

                //Computing hash sum of changes
                List<Integer> list_change_hash_sum = new ArrayList<Integer>();
                List<String> ruleNamesList2 = Arrays.asList(change.get_parser().getRuleNames());
                TreeUtils.tree_hash_sumAST(change.get_parsetree(), ruleNamesList2, list_change_hash_sum, change.features);

                //Computing list change parent child
                List<Integer> list_change_parent_child = new ArrayList<Integer>();
                TreeUtils.pairs_parent_childAST(change.get_parsetree(), ruleNamesList2, list_change_parent_child, change.features);

                // Writing the feature vector in a csv file
                StringBuilder str_builder = new StringBuilder();

                for (int element : change.features) {
                    str_builder.append(element);
                    str_builder.append(",");
                }
                str_builder.append("\n");

                buff_writer_features.write(str_builder.toString());

                // Writing the string change in a file
                bw.write(change.get_change_string().replace("\n", "$$") + "\n");
            }
            buff_writer_features.close();
            bw.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return changes_number;
    }

    /**
     * Creation of the query tree and extraction of its features.
     *
     * @param query_input: String of the query
     * @return AST of the query
     */
    public static Python3_Tree query_feature_extraction(String query_input){
        // String query_input = "import ID -> _";
        Python3_Tree tree_query = null;

        //Creating the tree for the query string
        try {
            tree_query = new Python3_Tree(query_input);

            if(TreeUtils.node_count(tree_query.get_parsetree(), Arrays.asList(tree_query.get_parser().getRuleNames()), 0) <= 5 ||
                    tree_query.isError() ||
                    tree_query.error)
                return null;

            //Declaring query variables
            List<Integer> list_parent_child = new ArrayList<Integer>();
            List<Integer> list_hash_sum = new ArrayList<Integer>();
            Python3BaseListener listener = new Python3BaseListener();
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(listener, tree_query.get_parsetree());
            List<String> ruleNamesList = Arrays.asList(tree_query.get_parser().getRuleNames());

            //Computing hash sum and pairs parent child
            TreeUtils.tree_hash_sumAST(tree_query.get_parsetree(), ruleNamesList, list_hash_sum, tree_query.features);
            TreeUtils.pairs_parent_childAST(tree_query.get_parsetree(), ruleNamesList, list_parent_child, tree_query.features);
            list_hash_sum.addAll(list_parent_child);

            //Creation of a buffered writer
            BufferedWriter buff_writer = new BufferedWriter(new FileWriter("./src/main/resources/Features_Vectors/query_feature_vectors.csv"));

            // Writing the feature vector in a csv file
            StringBuilder str_builder = new StringBuilder();

            for (int element : tree_query.features) {
                str_builder.append(element);
                str_builder.append(",");
            }
            str_builder.append("\n");

            buff_writer.write(str_builder.toString());
            buff_writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("QUERY TREE AND FEATURES COMPUTATION DONE");

        return tree_query;
    }

    /**
     * Method that creates a new process that launches Python script containing the FAISS Framework.
     *
     * @param
     * @return
     */
    public static void search_candidate_changes(){
        Process python_Nearest_Neighbor_Search;
        try {
            python_Nearest_Neighbor_Search = Runtime.getRuntime().exec(Config.pythonCmd + " ./src/main/resources/Python/Nearest_Neighbor_Search.py");
            BufferedReader br = new BufferedReader(new InputStreamReader(python_Nearest_Neighbor_Search.getInputStream()));

            python_Nearest_Neighbor_Search.waitFor();
            python_Nearest_Neighbor_Search.destroy();
        } catch (Exception e) { e.printStackTrace();}

        System.out.println("PYTHON STAGE DONE\n");
    }

    /**
     * Method that compute the cosine distance between query feature vector and change feature vectors.
     *
     * @param tree_query: query Tree
     * @return number of matching changes found
     */
    public static long final_matching(Python3_Tree tree_query){
        List<String> allLines = null;
        try {
            allLines = Files.readAllLines(Paths.get("./src/main/resources/Features_Vectors/candidate_changes.txt"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        int length = tree_query.features.length;
        double threshold = 0.8;
        long number_matching = 0;

        PrintWriter writer = null;
        try {
            writer = new PrintWriter(System.getProperty("user.dir") + "/src/main/resources/Features_Vectors/candidate_changes_filtered.txt", "UTF-8");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        for(String candidate : allLines){
            Python3_Tree change = new Python3_Tree(candidate.replace("$$", "\n"));
            //Computing hash sum of changes
            List<Integer> list_change_hash_sum = new ArrayList<Integer>();
            List<String> ruleNamesList2 = Arrays.asList(change.get_parser().getRuleNames());
            TreeUtils.tree_hash_sumAST(change.get_parsetree(), ruleNamesList2, list_change_hash_sum, change.features);

            //Computing list change parent child
            List<Integer> list_change_parent_child = new ArrayList<Integer>();
            TreeUtils.pairs_parent_childAST(change.get_parsetree(), ruleNamesList2, list_change_parent_child, change.features);

            double score =  Matching_Methods.cosineSimilarity(tree_query.features, change.features, length);

            if(score >= threshold) {
                number_matching++;
                writer.println(candidate.replace("\n", "$$") + "\n");
                System.out.println(candidate.replace("$$", "\n") + " score: " + score);
            }
        }

        writer.close();

        return number_matching;
    }

    /**
     * Method that implements a deep recursive comparison between query tree and change trees to find
     * matching changes.
     *
     * @param tree_query: query Tree
     * @return number of matching changes found
     */
    public static long final_comparison(Python3_Tree tree_query){

        List<String> allLines = null;
        try {
            allLines = Files.readAllLines(Paths.get("./src/main/resources/Features_Vectors/candidate_changes_filtered.txt"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        long number_matching = 0;

        List<String> list_query_nodes = new ArrayList<>();
        TreeUtils.query_extraction_nodes(tree_query.get_parsetree(), Arrays.asList(tree_query.get_parser().getRuleNames()), list_query_nodes);

        List<String> list_query_old_leaves = new ArrayList<>();
        List<String> list_query_new_leaves = new ArrayList<>();

        TreeUtils.query_leaves_extraction(tree_query.get_parsetree(), Arrays.asList(tree_query.get_parser().getRuleNames()), list_query_old_leaves);

        boolean new_code = false;

        for(String str: list_query_old_leaves){
            if(str.equals("->") || str.equals("<EOF>")){
                new_code = true;
            }else{
                if(new_code){
                   list_query_new_leaves.add(str);
                }
            }
        }

        String[] array_query_old_nodes = new String[list_query_old_leaves.size()];
        array_query_old_nodes = list_query_old_leaves.toArray(array_query_old_nodes);

        String[] array_query_new_nodes = new String[list_query_new_leaves.size()];
        array_query_new_nodes = list_query_new_leaves.toArray(array_query_new_nodes);

        assert allLines != null;
        for(String candidate : allLines){
            Python3_Tree change = new Python3_Tree(candidate.replace("$$", "\n"));

            List<String> list_change_nodes = new ArrayList<>();
            TreeUtils.query_extraction_nodes(change.get_parsetree(), Arrays.asList(change.get_parser().getRuleNames()), list_change_nodes);

            String s1 = change.get_tree_string();
            String s2 = tree_query.get_tree_string();

            PrintWriter writer = null;
            try {
                writer = new PrintWriter(System.getProperty("user.dir") + "/src/main/resources/Features_Vectors/log_file.txt", "UTF-8");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            boolean equal = TreeUtils.deep_tree_comparison(tree_query.get_parsetree(), Arrays.asList(tree_query.get_parser().getRuleNames()), change.get_parsetree(), Arrays.asList(change.get_parser().getRuleNames()), writer);

            writer.close();

            if(equal) {
                List<String> list_change_old_leaves = new ArrayList<>();
                List<String> list_change_new_leaves = new ArrayList<>();

                TreeUtils.query_leaves_extraction( change.get_parsetree(), Arrays.asList(change.get_parser().getRuleNames()), list_change_old_leaves);



                new_code = false;

                for(String str: list_change_old_leaves){
                    if(str.equals("->") || str.equals("<EOF>")){
                        new_code = true;
                    }else{
                        if(new_code){
                            list_change_new_leaves.add(str);
                        }
                    }
                }

                String[] array_change_old_nodes = new String[list_change_old_leaves.size()];
                array_change_old_nodes = list_change_old_leaves.toArray(array_change_old_nodes);

                String[] array_change_new_nodes = new String[list_change_new_leaves.size()];
                array_change_new_nodes = list_change_new_leaves.toArray(array_change_new_nodes);

                boolean final_matching = Matching_Methods.leaves_final_matching(array_query_old_nodes, array_query_new_nodes,array_change_old_nodes, array_change_new_nodes);

                if(final_matching){
                    number_matching++;

                    List<String> list = Arrays.asList(candidate.replace("$$", "\n").split("->"));
                    System.out.println("- " + list.get(0) + "\n+ " + list.get(1));
                }
            }
        }

        return number_matching;
    }
}