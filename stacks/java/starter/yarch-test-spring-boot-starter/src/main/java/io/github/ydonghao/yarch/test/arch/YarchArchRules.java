package io.github.ydonghao.yarch.test.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

/**
 * ArchUnit 分层规则集（J7 机检三件套之二）：把「分层纪律」从 CR 自觉变成测试失败。 ddd 档约束依赖倒置（domain 零框架依赖）；simple
 * 档约束经典单向（controller 不碰 dao）。
 */
public final class YarchArchRules {

    private YarchArchRules() {}

    /** ddd 七包（api/application/domain/crossdomain/infrastructure/types/common） */
    public static ArchRule[] ddd() {
        return new ArchRule[] {
            noClasses()
                    .that()
                    .resideInAPackage("..api..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..infrastructure..")
                    .because("api 层只做接入（参数校验/信封），技术实现经 application/domain 间接到达"),
            noClasses()
                    .that()
                    .resideInAPackage("..application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..infrastructure..")
                    .because("application 编排面向 domain 的 repository 接口，不直接触碰技术实现"),
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..", "com.baomidou..", "jakarta.servlet..")
                    .because("domain 零框架依赖（充血模型可独立单测；仓储是接口，实现在 infrastructure）"),
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..api..", "..application..")
                    .because("依赖倒置：外层指向 domain，domain 不感知外层"),
            classes()
                    .that()
                    .resideInAPackage("..api.controller..")
                    .should()
                    .haveSimpleNameEndingWith("Controller")
                    .because("接入层命名后缀固定"),
        };
    }

    /** 阿里五层裁剪（controller/service/manager/dao/model） */
    public static ArchRule[] simple() {
        return new ArchRule[] {
            noClasses()
                    .that()
                    .resideInAPackage("..controller..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..dao..", "..mapper..")
                    .because("Web 层只调 Service/Manager，不直接碰数据访问（阿里分层规约）"),
            noClasses()
                    .that()
                    .resideInAPackage("..service..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage("..controller..")
                    .because("依赖只能自上而下：controller → service → dao"),
            classes()
                    .that()
                    .resideInAPackage("..controller..")
                    .should()
                    .haveSimpleNameEndingWith("Controller")
                    .because("接入层命名后缀固定"),
        };
    }

    /** ddd 工程自检入口：Application 类作导入锚点 */
    public static void checkDdd(Class<?> applicationEntryPoint) {
        check(ddd(), applicationEntryPoint);
    }

    /** simple 工程自检入口 */
    public static void checkSimple(Class<?> applicationEntryPoint) {
        check(simple(), applicationEntryPoint);
    }

    private static void check(ArchRule[] rules, Class<?> entryPoint) {
        JavaClasses classes = new ClassFileImporter().importPackagesOf(entryPoint);
        for (ArchRule rule : rules) {
            rule.check(classes);
        }
    }
}
