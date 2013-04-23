package com.evolveum.midpoint.wf.activiti;

import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.wf.WorkflowServiceImpl;
import com.evolveum.midpoint.wf.api.WorkflowException;
import org.activiti.engine.ActivitiException;
import org.activiti.engine.HistoryService;
import org.activiti.engine.TaskService;
import org.activiti.engine.history.HistoricDetail;
import org.activiti.engine.history.HistoricDetailQuery;
import org.activiti.engine.history.HistoricVariableUpdate;
import org.activiti.engine.task.IdentityLink;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Auxiliary methods for accessing data in Activiti.
 *
 * @author mederly
 */

@Component
public class ActivitiEngineDataHelper {

    private static final Trace LOGGER = TraceManager.getTrace(ActivitiEngineDataHelper.class);

    @Autowired
    private ActivitiEngine activitiEngine;

    public Map<String, Object> getHistoricVariables(String pid, OperationResult result) throws WorkflowException {

        Map<String, Object> retval = new HashMap<String, Object>();

        // copied from ActivitiInterface!
        HistoryService hs = activitiEngine.getHistoryService();

        try {

            HistoricDetailQuery hdq = hs.createHistoricDetailQuery()
                    .variableUpdates()
                    .processInstanceId(pid)
                    .orderByTime().desc();

            for (HistoricDetail hd : hdq.list())
            {
                HistoricVariableUpdate hvu = (HistoricVariableUpdate) hd;
                String name = hvu.getVariableName();
                Object value = hvu.getValue();
                if (!retval.containsKey(name)) {
                    retval.put(name, value);
                }
            }

            return retval;

        } catch (ActivitiException e) {
            String m = "Couldn't get variables for finished process instance " + pid;
            result.recordFatalError(m, e);
            throw new WorkflowException(m, e);
        }
    }

    public Map<String,Object> getProcessVariables(String taskId, OperationResult result) throws ObjectNotFoundException, WorkflowException {
        try {
            Task task = getTask(taskId);
            Map<String,Object> variables = activitiEngine.getProcessEngine().getRuntimeService().getVariables((task.getExecutionId()));
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Execution " + task.getExecutionId() + ", pid " + task.getProcessInstanceId() + ", variables = " + variables);
            }
            return variables;
        } catch (ActivitiException e) {
            String m = "Couldn't get variables for the process corresponding to task " + taskId;
            result.recordFatalError(m, e);
            throw new WorkflowException(m, e);
        }
    }

    // todo deduplicate this
    // todo: ObjectNotFoundException used in unusual way (not in connection with midPoint repository)
    private Task getTask(String taskId) throws ObjectNotFoundException {
        Task task = activitiEngine.getTaskService().createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new ObjectNotFoundException("Task " + taskId + " could not be found.");
        }
        return task;
    }

    public Task getTaskById(String taskId, OperationResult result) throws ObjectNotFoundException {
        TaskService taskService = activitiEngine.getTaskService();
        TaskQuery tq = taskService.createTaskQuery();
        tq.taskId(taskId);
        Task task = tq.singleResult();
        if (task == null) {
            result.recordFatalError("Task with ID " + taskId + " does not exist.");
            throw new ObjectNotFoundException("Task with ID " + taskId + " does not exist.");
        } else {
            return task;
        }
    }


    public List<String> getCandidates(Task task) {

        List<String> retval = new ArrayList<String>();

        TaskService taskService = activitiEngine.getTaskService();

        List<IdentityLink> ils = taskService.getIdentityLinksForTask(task.getId());
        for (IdentityLink il : ils) {
            if ("candidate".equals(il.getType())) {
                if (il.getGroupId() != null) {
                    retval.add("G:" + il.getGroupId());
                }
                if (il.getUserId() != null) {
                    retval.add("U:" + il.getUserId());
                }
            }
        }

        return retval;
    }

    public String getCandidatesAsString(Task task) {

        StringBuilder retval = new StringBuilder();
        boolean first = true;
        for (String c : getCandidates(task)) {
            if (first) {
                first = false;
            } else {
                retval.append(", ");
            }
            retval.append(c);
        }
        return retval.toString();
    }


}