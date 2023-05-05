package research.diffsearch.main;

import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import research.diffsearch.Config;
import research.diffsearch.server.PythonRunner;

public class BugSwarmMode extends App {
    private static final Logger logger = LoggerFactory.getLogger(BugSwarmMode.class);

    @Override
    public void run() {
        var absPath = Paths.get(Config.repositoryPath).toAbsolutePath().normalize();
        logger.info("Generating patches...");

        try {
            DatasetCreationMode.extractCommitLogs();

            String mode = Config.bugswarm_artifact_path.isEmpty() ? "buildpairs" : "artifacts";
            var runner = new PythonRunner(
                    "./src/main/resources/Python/bugswarm_gen_patches.py",
                    absPath.toString(),
                    Config.PROGRAMMING_LANGUAGE.getSuffix(),
                    mode,
                    Config.bugswarm_artifact_path);
            runner.runAndWaitUntilEnd();

            DatasetCreationMode.extractAndSaveChanges();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }
}
