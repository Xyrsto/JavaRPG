package dev.javarpg.server.config;

import dev.javarpg.core.contract.Evaluator;
import dev.javarpg.core.contract.Grader;
import dev.javarpg.core.contract.SandboxExecutor;
import dev.javarpg.runner.grading.DefaultGrader;
import dev.javarpg.runner.grading.GradingPipeline;
import dev.javarpg.server.service.RunGate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Sandbox and grading beans. The executor is chosen by javarpg.sandbox.mode and fails fast on unknown values. */
@Configuration
public class SandboxConfig {

    @Bean
    public SandboxExecutor sandboxExecutor(JavaRpgProperties props, Environment env) {
        return new SandboxExecutorFactory().create(props, env);
    }

    @Bean
    public Grader grader() {
        return new DefaultGrader();
    }

    @Bean
    public Evaluator evaluator(SandboxExecutor executor, Grader grader) {
        return new GradingPipeline(executor, grader);
    }

    @Bean
    public RunGate runGate(JavaRpgProperties props) {
        return new RunGate(props.getSandbox().getMaxConcurrent(), props.getSandbox().getAcquireWaitMillis());
    }
}
