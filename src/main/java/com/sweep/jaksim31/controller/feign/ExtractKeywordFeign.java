package com.sweep.jaksim31.controller.feign;


import com.sweep.jaksim31.controller.feign.config.ExtractKeywordFeignConfig;
import com.sweep.jaksim31.dto.diary.extractkeyword.ExtractKeywordRequest;
import com.sweep.jaksim31.dto.diary.extractkeyword.ExtractKeywordResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@FeignClient(name = "extractKeyword2", url= "${extract-api.url}", configuration = ExtractKeywordFeignConfig.class)
public interface ExtractKeywordFeign {
    @RequestMapping(method = RequestMethod.POST)
    ResponseEntity<ExtractKeywordResponse> extractKeyword(@RequestBody ExtractKeywordRequest sentence);
}


