package com.nepse.portfolio.alert;

import com.nepse.portfolio.alert.AlertDtos.AlertDto;
import com.nepse.portfolio.alert.AlertDtos.AlertEventDto;
import com.nepse.portfolio.alert.AlertDtos.CreateAlertRequest;
import com.nepse.portfolio.alert.AlertDtos.UpdateAlertRequest;
import com.nepse.portfolio.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;
    private final CurrentUser currentUser;

    public AlertController(AlertService alertService, CurrentUser currentUser) {
        this.alertService = alertService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<AlertDto> list() {
        return alertService.list(currentUser.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AlertDto create(@Valid @RequestBody CreateAlertRequest request) {
        return alertService.create(currentUser.id(), request);
    }

    @PutMapping("/{id}")
    public AlertDto update(@PathVariable Long id, @Valid @RequestBody UpdateAlertRequest request) {
        return alertService.update(currentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        alertService.delete(currentUser.id(), id);
    }

    @GetMapping("/events")
    public List<AlertEventDto> events() {
        return alertService.events(currentUser.id());
    }
}
