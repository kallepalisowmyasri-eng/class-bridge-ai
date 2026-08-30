package com.aiclassrecovery.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aiclassrecovery.model.ClassRecord;
import com.aiclassrecovery.service.RecoveryEngineService;

@RestController
@RequestMapping("/api/classes/{id}")
public class RecoveryEngineController {

    private final RecoveryEngineService recoveryEngineService;

    public RecoveryEngineController(RecoveryEngineService recoveryEngineService) {
        this.recoveryEngineService = recoveryEngineService;
    }

    @GetMapping("/missed")
    public ResponseEntity<Map<String, Object>> getWhatYouMissed(Authentication auth,
                                                                 @PathVariable String id) {
        return ResponseEntity.ok(recoveryEngineService.getWhatYouMissed(auth.getName(), id));
    }

    @GetMapping("/must-learn")
    public ResponseEntity<List<ClassRecord.MustLearnItem>> getMustLearn(Authentication auth,
                                                                         @PathVariable String id) {
        return ResponseEntity.ok(recoveryEngineService.getMustLearn(auth.getName(), id));
    }

    @GetMapping("/catchup-path")
    public ResponseEntity<List<String>> getCatchUpPath(Authentication auth,
                                                        @PathVariable String id) {
        return ResponseEntity.ok(recoveryEngineService.getCatchUpPath(auth.getName(), id));
    }

    @GetMapping("/prerequisites")
    public ResponseEntity<Map<String, Object>> getPrerequisites(Authentication auth,
                                                                 @PathVariable String id) {
        return ResponseEntity.ok(recoveryEngineService.getPrerequisites(auth.getName(), id));
    }

    @GetMapping("/explanations")
    public ResponseEntity<Map<String, Object>> getExplanations(Authentication auth,
                                                                @PathVariable String id) {
        return ResponseEntity.ok(recoveryEngineService.getExplanations(auth.getName(), id));
    }

    @GetMapping("/announcements")
    public ResponseEntity<List<Map<String, String>>> getAnnouncements(Authentication auth,
                                                                       @PathVariable String id) {
        return ResponseEntity.ok(recoveryEngineService.getAnnouncements(auth.getName(), id));
    }

    @GetMapping("/assignments")
    public ResponseEntity<List<Map<String, String>>> getAssignments(Authentication auth,
                                                                     @PathVariable String id) {
        return ResponseEntity.ok(recoveryEngineService.getAssignments(auth.getName(), id));
    }

    @GetMapping("/resources")
    public ResponseEntity<List<Map<String, Object>>> getResources(Authentication auth,
                                                                   @PathVariable String id) {
        return ResponseEntity.ok(recoveryEngineService.getResources(auth.getName(), id));
    }

    @GetMapping("/timestamps")
    public ResponseEntity<List<ClassRecord.TimestampedItem>> getTimestamps(Authentication auth,
                                                                            @PathVariable String id) {
        return ResponseEntity.ok(recoveryEngineService.getTimestamps(auth.getName(), id));
    }

    @GetMapping("/revision")
    public ResponseEntity<List<String>> getRevision(Authentication auth,
                                                     @PathVariable String id) {
        return ResponseEntity.ok(recoveryEngineService.getRevision(auth.getName(), id));
    }

    @GetMapping("/quiz")
    public ResponseEntity<List<ClassRecord.QuizQuestion>> getQuiz(Authentication auth,
                                                                   @PathVariable String id) {
        return ResponseEntity.ok(recoveryEngineService.getQuiz(auth.getName(), id));
    }

    @GetMapping("/watch")
    public ResponseEntity<Map<String, Object>> getWhatToWatch(Authentication auth,
                                                               @PathVariable String id) {
        return ResponseEntity.ok(recoveryEngineService.getWhatToWatch(auth.getName(), id));
    }
}
