package com.pspswitch.rules.client;

import com.pspswitch.rules.model.ValidationRequest;
import com.pspswitch.rules.model.ValidationResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "rules-validation-service", url = "${rules.service.url:http://localhost:8085}")
public interface RulesValidationFeignClient {

    @PostMapping("/rules/validate")
    ValidationResponse validate(@RequestBody ValidationRequest request);
}
