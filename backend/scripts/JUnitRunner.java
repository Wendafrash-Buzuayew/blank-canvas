// Minimal JUnit Platform runner for when ./gradlew cannot run in this environment
// (see the verify-backend skill). There is no junit-platform-console-standalone jar
// in the local Gradle cache -- only the launcher/engine libraries Gradle itself uses
// -- so this drives org.junit.platform.launcher's API directly instead of shelling
// out to a console launcher tool.
//
// Compile once against the resolved classpath (see gradle-classpath.mjs), then run
// with the SAME classpath plus the directory the runner's own .class file lives in,
// plus the directories holding the compiled test/main classes under test.
//
// Usage: java JUnitRunner com.qrserve.shared.security.JwtTokenProviderTokenTypeTest ...
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.io.PrintWriter;

public class JUnitRunner {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: java JUnitRunner <fully.qualified.TestClass> [more...]");
            System.exit(2);
        }

        var selectors = new java.util.ArrayList<org.junit.platform.engine.DiscoverySelector>();
        for (String className : args) {
            Class<?> testClass;
            try {
                testClass = Class.forName(className);
            } catch (ClassNotFoundException e) {
                System.err.println("Class not found on classpath: " + className);
                System.exit(2);
                return;
            }
            selectors.add(selectClass(testClass));
        }

        LauncherDiscoveryRequest request = request()
                .selectors(selectors)
                .build();

        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        TestExecutionSummary summary = listener.getSummary();
        PrintWriter writer = new PrintWriter(System.out);
        summary.printTo(writer);
        summary.printFailuresTo(writer, 100);
        writer.flush();

        System.exit(summary.getTotalFailureCount() == 0 ? 0 : 1);
    }
}
