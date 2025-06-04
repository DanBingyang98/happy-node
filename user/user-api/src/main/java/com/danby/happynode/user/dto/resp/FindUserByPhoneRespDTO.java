package com.danby.happynode.user.dto.resp;

import com.danby.happynode.framework.common.validator.PhoneNumber;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindUserByPhoneRespDTO {

    private Long id;

    private String password;

}
