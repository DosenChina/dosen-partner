/**
 * Copyright (c) 2026 dosen-partner Contributors.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dosen.llm.partner.agent.controller;


import com.dosen.llm.partner.agent.model.ChatMessages;
import com.dosen.llm.partner.agent.service.MongoCRUDTest;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/mongo/test")
public class MongoCRUDController {

    private final MongoCRUDTest mongoCRUDTest;

    @GetMapping("/insert")
    public ChatMessages testInsert(@RequestParam(value = "id", required = false) Long id,
                                   @RequestParam("content") String content) {
        return mongoCRUDTest.testInsert(content);
    }

    @GetMapping("/query")
    public ChatMessages testQuery(@RequestParam("id") String id) {
        return mongoCRUDTest.testQuery(id);
    }

    @GetMapping("/update")
    public Object testUpdate(@RequestParam("id") String id,
                             @RequestParam("content") String content) {
        return mongoCRUDTest.testUpdate(id, content);
    }

    @GetMapping("/delete")
    public Object testDelete(@RequestParam("id") Long id) {
        return mongoCRUDTest.testDelete(id);
    }

    @GetMapping("/delete/object")
    public Object testDeleteObject(@RequestParam("id") String id) {
        return mongoCRUDTest.testDelete(id);
    }

}
