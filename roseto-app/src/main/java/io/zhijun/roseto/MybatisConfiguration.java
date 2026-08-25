package io.zhijun.roseto;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("io.zhijun.roseto.infrastructure.persistence")
public class MybatisConfiguration {
}
