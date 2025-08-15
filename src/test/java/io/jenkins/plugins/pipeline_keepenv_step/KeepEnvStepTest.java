/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.jenkins.plugins.pipeline_keepenv_step;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.base.Predicates;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.NodeStepTypePredicate;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class KeepEnvStepTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();

    private JenkinsRule j;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void keepOnlyThePassedVariables() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                        env.A = 'value-a'
                        env.B = 'value-b'
                        env.C = 'value-c'
                        node {
                          isUnix() ? sh('echo a-b-c-1 A=$A B=$B C=$C D=$D end') : bat('echo a-b-c-1 A=%A% B=%B% C=%C% D=%D% end')
                          keepEnv(['A', 'B']){
                            isUnix() ? sh('echo only-a-b A=$A B=$B C=$C D=$D end') : bat('echo only-a-b A=%A% B=%B% C=%C% D=%D% end')
                            withEnv(['D=value-d']){
                              isUnix() ? sh('echo with-d A=$A B=$B C=$C D=$D end') : bat('echo with-d A=%A% B=%B% C=%C% D=%D% end')
                              keepEnv(['B', 'D']){
                                isUnix() ? sh('echo only-b-d A=$A B=$B C=$C D=$D end') : bat('echo only-b-d A=%A% B=%B% C=%C% D=%D% end')
                              }
                            }
                            keepEnv(['A']){
                              isUnix() ? sh('echo only-a A=$A B=$B C=$C D=$D end') : bat('echo only-a A=%A% B=%B% C=%C% D=%D% end')
                            }
                            keepEnv(['B']){
                              isUnix() ? sh('echo only-b A=$A B=$B C=$C D=$D end') : bat('echo only-b A=%A% B=%B% C=%C% D=%D% end')
                            }
                          }
                          isUnix() ? sh('echo a-b-c-2 A=$A B=$B C=$C D=$D end') : bat('echo a-b-c-2 A=%A% B=%B% C=%C% D=%D% end')
                        }""",
                true));
        WorkflowRun b = j.buildAndAssertSuccess(p);
        j.assertLogContains("a-b-c-1 A=value-a B=value-b C=value-c D= end", b);
        j.assertLogContains("only-a-b A=value-a B=value-b C= D= end", b);
        j.assertLogContains("with-d A=value-a B=value-b C= D=value-d end", b);
        j.assertLogContains("only-b-d A= B=value-b C= D=value-d end", b);
        j.assertLogContains("only-a A=value-a B= C= D= end", b);
        j.assertLogContains("only-b A= B=value-b C= D= end", b);
        j.assertLogContains("a-b-c-2 A=value-a B=value-b C=value-c D= end", b);
        List<FlowNode> coreStepNodes = new DepthFirstScanner()
                .filteredNodes(
                        b.getExecution(),
                        Predicates.and(
                                new NodeStepTypePredicate("keepEnv"),
                                n -> n instanceof StepStartNode && !((StepStartNode) n).isBody()));
        assertThat(coreStepNodes, hasSize(4));
        assertEquals("A, B", ArgumentsAction.getStepArgumentsAsString(coreStepNodes.get(3)));
        assertEquals("B, D", ArgumentsAction.getStepArgumentsAsString(coreStepNodes.get(2)));
        assertEquals("A", ArgumentsAction.getStepArgumentsAsString(coreStepNodes.get(1)));
        assertEquals("B", ArgumentsAction.getStepArgumentsAsString(coreStepNodes.get(0)));
    }

    @Test
    void configRoundTrip() throws Exception {
        configRoundTrip(Collections.emptyList());
        configRoundTrip(Collections.singletonList("VAR"));
        configRoundTrip(Arrays.asList("VAR1", "VAR2"));
    }

    private void configRoundTrip(List<String> variablesToKeep) throws Exception {
        assertEquals(
                variablesToKeep,
                new StepConfigTester(j)
                        .configRoundTrip(new KeepEnvStep(variablesToKeep))
                        .getVariables());
    }
}
