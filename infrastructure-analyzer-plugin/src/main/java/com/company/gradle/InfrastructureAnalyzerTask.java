package com.company.gradle;

import com.company.gradle.analyzer.DeploymentDetector;
import com.company.gradle.analyzer.InfrastructureExtractor;
import com.company.gradle.model.*;
import com.company.gradle.util.YamlParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/**
 * 인프라 검증 항목을 분석하고 requirements.json을 생성하는 Gradle Task
 */
public class InfrastructureAnalyzerTask extends DefaultTask {

    private static final String[] PROFILES = {"dev", "stg", "prod"};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @TaskAction
    public void analyze() {
        // 1. 환경 감지
        DeploymentType deploymentType = DeploymentDetector.detect(getProject());
        getLogger().lifecycle("✅ 감지된 배포 환경: {}", deploymentType);

        // 2. YAML 파일 위치
        File appYml = new File(getProject().getProjectDir(),
            "src/main/resources/application.yml");

        if (!appYml.exists()) {
            getLogger().warn("⚠️  application.yml을 찾을 수 없습니다: {}", appYml.getPath());
            return;
        }

        // 3. 프로파일별 requirements.json 생성
        File outputDir = getProject().getLayout().getBuildDirectory().dir("infrastructure").get().getAsFile();
        outputDir.mkdirs();

        for (String profile : PROFILES) {
            Map<String, Object> config = YamlParser.parseWithProfile(appYml, profile);

            if (config.isEmpty()) {
                getLogger().info("ℹ️  {} 프로파일 설정이 없습니다. 기본 설정만 사용합니다.", profile);
                config = YamlParser.parseWithProfile(appYml, null);
            }

            Requirements requirements;
            if (deploymentType == DeploymentType.KUBERNETES) {
                requirements = generateK8sRequirements(profile, config);
            } else {
                requirements = generateVmRequirements(profile, config);
            }

            // JSON 파일 생성
            String filename = (deploymentType == DeploymentType.KUBERNETES)
                ? "requirements-k8s-" + profile + ".json"
                : "requirements-" + profile + ".json";

            File outputFile = new File(outputDir, filename);
            writeJson(requirements, outputFile);
            getLogger().lifecycle("✅ 생성됨: {}", filename);
        }

        // 4. 검증 스크립트 복사
        copyValidationScript(deploymentType);
    }

    private Requirements generateVmRequirements(String profile, Map<String, Object> config) {
        InfrastructureExtractor extractor = new InfrastructureExtractor(config);

        Requirements req = new Requirements();
        req.setProject(getProject().getName());
        req.setEnvironment(profile);
        req.setPlatform("vm");

        Requirements.Infrastructure infra = req.getInfrastructure();
        infra.setCompany_domain(extractor.getCompanyDomain());
        infra.setFiles(extractor.extractFiles());
        infra.setExternal_apis(extractor.extractApis());

        return req;
    }

    private Requirements generateK8sRequirements(String profile, Map<String, Object> config) {
        InfrastructureExtractor extractor = new InfrastructureExtractor(config);

        Requirements req = new Requirements();
        req.setProject(getProject().getName());
        req.setEnvironment(profile);
        req.setPlatform("kubernetes");

        Requirements.Infrastructure infra = req.getInfrastructure();
        infra.setCompany_domain(extractor.getCompanyDomain());
        infra.setNamespace(InfrastructureExtractor.determineNamespace(profile));
        infra.setFiles(extractor.extractFiles());
        infra.setExternal_apis(extractor.extractApis());
        infra.setConfigmaps(extractor.extractConfigMaps());
        infra.setSecrets(extractor.extractSecrets());
        infra.setPvcs(extractor.extractPvcs());

        return req;
    }

    private void writeJson(Requirements requirements, File outputFile) {
        try (FileWriter writer = new FileWriter(outputFile)) {
            GSON.toJson(requirements, writer);
        } catch (IOException e) {
            getLogger().error("❌ JSON 파일 생성 실패: {}", outputFile.getPath(), e);
        }
    }

    private void copyValidationScript(DeploymentType deploymentType) {
        File targetDir = new File(getProject().getProjectDir(), "bamboo-scripts");

        String scriptName = (deploymentType == DeploymentType.KUBERNETES)
            ? "validate-k8s-infrastructure.sh"
            : "validate-infrastructure.sh";

        File targetFile = new File(targetDir, scriptName);

        // 이미 있으면 건너뜀
        if (targetFile.exists()) {
            getLogger().info("ℹ️  검증 스크립트가 이미 존재합니다: {}", scriptName);
            return;
        }

        targetDir.mkdirs();

        try (InputStream is = getClass().getResourceAsStream("/" + scriptName)) {
            if (is == null) {
                getLogger().warn("⚠️  리소스에서 스크립트를 찾을 수 없습니다: {}", scriptName);
                return;
            }
            Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            targetFile.setExecutable(true);

            getLogger().lifecycle("✅ 생성됨: bamboo-scripts/{}", scriptName);
            getLogger().lifecycle("⚠️  Git에 커밋해주세요:");
            getLogger().lifecycle("   git add bamboo-scripts/");
            getLogger().lifecycle("   git commit -m \"chore: add infrastructure validation script\"");
        } catch (IOException e) {
            getLogger().error("❌ 스크립트 복사 실패: {}", scriptName, e);
        }
    }
}
