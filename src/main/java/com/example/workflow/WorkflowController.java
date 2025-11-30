package com.example.workflow;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

//@RestController
//@RequiredArgsConstructor
//@RequestMapping("/workflow")
//public class WorkflowController {
//
//    private final OrchestratorService orchestrator;
//
//    @PostMapping(value = "/run", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
//    public WorkflowResponse run(@RequestBody WorkflowRequest request) {
//        return orchestrator.runWorkflow(request);
//    }
//}


@RestController
@RequestMapping("/workflow")
@RequiredArgsConstructor
public class WorkflowController {
    private final OrchestratorService orchestrator;

    @GetMapping("/")
    public String index() {
        return "redirect:/index.html";
    }

    @PostMapping("/run")
    public WorkflowResponse run(@RequestBody WorkflowRequest req) throws Exception {
        return orchestrator.run(req);
    }
    
    @PostMapping("/resume")
    public WorkflowResponse resume(@RequestBody WorkflowRequest req) throws Exception {
        if (req.requestId() == null || req.requestId().isBlank()) {
            throw new IllegalArgumentException("requestId is required for resume");
        }
        return orchestrator.resumeWorkflow(req.requestId(), req);
    }
}
