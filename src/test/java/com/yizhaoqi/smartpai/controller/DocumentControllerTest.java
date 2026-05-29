package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.config.JwtAuthenticationFilter;
import com.yizhaoqi.smartpai.config.OrgTagAuthorizationFilter;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.repository.OrganizationTagRepository;
import com.yizhaoqi.smartpai.service.DocumentService;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
class DocumentControllerTest {

    private static final String FILE_MD5 = "0123456789abcdef0123456789abcdef";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private FileUploadRepository fileUploadRepository;

    @MockBean
    private OrganizationTagRepository organizationTagRepository;

    @MockBean
    private JwtUtils jwtUtils;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private OrgTagAuthorizationFilter orgTagAuthorizationFilter;

    @Test
    void deleteDocumentReturnsOkWhenServiceSucceeds() throws Exception {
        mockMvc.perform(delete("/api/v1/documents/{fileMd5}", FILE_MD5)
                        .requestAttr("userId", "owner")
                        .requestAttr("role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("文档删除成功"));

        verify(documentService).deleteDocument(FILE_MD5, "owner", "USER");
    }

    @Test
    void deleteDocumentMapsNotFoundException() throws Exception {
        doThrow(new CustomException("文档不存在", HttpStatus.NOT_FOUND))
                .when(documentService).deleteDocument(FILE_MD5, "owner", "USER");

        mockMvc.perform(delete("/api/v1/documents/{fileMd5}", FILE_MD5)
                        .requestAttr("userId", "owner")
                        .requestAttr("role", "USER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("文档不存在"));
    }

    @Test
    void deleteDocumentMapsForbiddenException() throws Exception {
        doThrow(new CustomException("没有权限删除此文档", HttpStatus.FORBIDDEN))
                .when(documentService).deleteDocument(FILE_MD5, "other-user", "USER");

        mockMvc.perform(delete("/api/v1/documents/{fileMd5}", FILE_MD5)
                        .requestAttr("userId", "other-user")
                        .requestAttr("role", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("没有权限删除此文档"));
    }
}
