package com.danby.happynode.auth.model.vo.verification;

import com.danby.happynode.framework.common.validator.PhoneNumber;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class VerificationVO {
    @NotBlank(message = "手机号不能为空")
    @PhoneNumber
    private String phone;
}
