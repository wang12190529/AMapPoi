package com.civitasv.spider.viewmodel;

import com.civitasv.spider.controller.POIController;
import com.civitasv.spider.helper.Enum.*;
import com.civitasv.spider.helper.exception.NoTryAgainException;
import com.civitasv.spider.helper.exception.TryAgainException;
import com.civitasv.spider.model.Feature;
import com.civitasv.spider.model.bo.Job;
import com.civitasv.spider.model.bo.POI;
import com.civitasv.spider.model.bo.Task;
import com.civitasv.spider.model.po.JobPo;
import com.civitasv.spider.model.po.TaskPo;
import com.civitasv.spider.service.JobService;
import com.civitasv.spider.service.PoiService;
import com.civitasv.spider.service.TaskService;
import com.civitasv.spider.service.serviceImpl.JobServiceImpl;
import com.civitasv.spider.service.serviceImpl.PoiServiceImpl;
import com.civitasv.spider.service.serviceImpl.TaskServiceImpl;
import com.civitasv.spider.util.*;
import com.civitasv.spider.webdao.AMapDao;
import com.civitasv.spider.webdao.impl.AMapDaoImpl;
import com.google.gson.*;
import com.opencsv.CSVWriter;
import com.spatial4j.core.io.GeohashUtils;
import javafx.application.Platform;
import javafx.scene.control.*;
import lombok.Builder;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.geotools.data.DataUtilities;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.type.DateUtil;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.json.simple.JSONArray;
import org.locationtech.jts.geom.*;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class POIViewModel {
    private final ViewHolder viewHolder;
    private final ConfigHolder configHolder;

    private final AMapDao mapDao;
    private final TaskService taskService;
    private final JobService jobService;
    private final PoiService poiService;

    private ExecutorService worker, executorService;
    private ExecutorCompletionService<Job> completionService;

    public void outputFields(List<POI.OutputFields> outputFields) {
        this.viewHolder.outputFields = outputFields;
    }

    public POIViewModel(POIController controller) {
        this.viewHolder = ViewHolder.builder()
                .threadNum(controller.threadNum)
                .keywords(controller.keywords)
                .keys(controller.keys)
                .types(controller.types)
                .adCode(controller.adCode)
                .rectangle(controller.rectangle)
                .threshold(controller.threshold)
                .format(controller.format)
                .outputDirectory(controller.outputDirectory)
                .messageDetail(controller.messageDetail)
                .userFile(controller.userFile)
                .tabs(controller.tabs)
                .directoryBtn(controller.directoryBtn)
                .execute(controller.execute)
                .poiType(controller.poiType)
                .userType(controller.userType)
                .rectangleCoordinateType(controller.rectangleCoordinateType)
                .userFileCoordinateType(controller.userFileCoordinateType)
                .wechat(controller.wechat)
                .joinQQ(controller.joinQQ)
                .poiCateBig(controller.poiCateBig)
                .poiCateMid(controller.poiCateMid)
                .poiCateSub(controller.poiCateSub)
                .poiCateAddBtn(controller.poiCateAddBtn)
                .openQPSBtn(controller.openQPSBtn)
                .chooseOutputFieldsBtn(controller.chooseOutputFieldsBtn)
                .build();
        this.viewHolder.initOutputFields();
        this.configHolder = new ConfigHolder();
        this.mapDao = new AMapDaoImpl();
        this.taskService = new TaskServiceImpl();
        this.jobService = new JobServiceImpl();
        this.poiService = new PoiServiceImpl();
    }

    @Builder
    private static class ViewHolder {
        public TextField threadNum; // 线程数目
        public TextField keywords; // 关键字
        public TextArea keys; // 高德 API Key
        public TextField types; // 类型
        public TextField adCode; // 行政区六位代码
        public TextField rectangle; // 矩形左上角#矩形右下角
        public TextField threshold; // 阈值
        public ChoiceBox<OutputType> format; // 输出格式
        public TextField outputDirectory; // 输出文件夹
        public Button openQPSBtn; // 查看 QPS
        public Button chooseOutputFieldsBtn; // 选择输出字段
        public List<POI.OutputFields> outputFields; // 输出字段
        public TextArea messageDetail; // 输出信息
        public TextField userFile; // 用户自定义文件
        public TabPane tabs; // tab 栏
        public Button directoryBtn; // 点击选择文件夹
        public Button execute; // 执行
        public Button poiType; // 点击查看 poi 类型
        public ChoiceBox<UserType> userType; // 用户类型
        public ChoiceBox<CoordinateType> rectangleCoordinateType; // 矩形坐标格式
        public ChoiceBox<CoordinateType> userFileCoordinateType; // 用户自定义文件坐标格式
        public MenuItem wechat; // 微信
        public MenuItem joinQQ; // QQ群

        public ChoiceBox<String> poiCateBig; // POI大类
        public ChoiceBox<String> poiCateMid; // POI中类
        public ChoiceBox<String> poiCateSub; // POI小类
        public Button poiCateAddBtn; // poi添加

        void initOutputFields() {
            this.outputFields = Arrays.stream(POI.OutputFields.values())
                    .filter(POI.OutputFields::checked)
                    .collect(Collectors.toList());
        }
    }

    private static class ConfigHolder {
        public static final int SIZE = 20;
        public Queue<String> aMapKeys;
        public Integer threadNum;
        public Integer threshold;
        private Integer qps;
        public Integer perExecuteTime;
        public String keywords;
        public String types;
        public String tab;
        public Double[] boundary;
        public String extension = "base";
        public boolean haveSavedUnfinishedJobs = false;
        public boolean hasStart = false;
        public int waitFactorForQps = 0;
        public final Set<TryAgainErrorCode> errorCodeHashSet = Arrays.stream(new Integer[]{10019, 10020, 10021, 10022, 10014, 10015}).map(TryAgainErrorCode::getError).collect(Collectors.toSet());
    }

    private void otherParams() {
        configHolder.haveSavedUnfinishedJobs = false;
        configHolder.hasStart = false;
        configHolder.waitFactorForQps = 0;
    }

    private boolean check() {
        if (viewHolder.keys.getText().isEmpty()) {
            // keys为空
            Platform.runLater(() -> MessageUtil.alert(Alert.AlertType.ERROR, "高德key", null, "高德key池不能为空！"));
            return false;
        }
        if (viewHolder.userType.getSelectionModel().getSelectedIndex() == -1) {
            Platform.runLater(() -> MessageUtil.alert(Alert.AlertType.ERROR, "用户类型", null, "请选择用户类型！"));
            return false;
        }
        if (viewHolder.keywords.getText().isEmpty() && viewHolder.types.getText().isEmpty()) {
            // 关键字和类型均为空
            Platform.runLater(() -> MessageUtil.alert(Alert.AlertType.ERROR, "参数设置", null, "POI关键字和POI类型两者至少必填其一！"));
            return false;
        }
        if (viewHolder.threshold.getText().isEmpty()) {
            // 阈值为空
            Platform.runLater(() -> MessageUtil.alert(Alert.AlertType.ERROR, "阈值", null, "阈值不能为空！"));
            return false;
        }
        if (viewHolder.threadNum.getText().isEmpty()) {
            // 线程数目为空
            Platform.runLater(() -> MessageUtil.alert(Alert.AlertType.ERROR, "线程数目", null, "线程数目不能为空！"));
            return false;
        }
        if (viewHolder.format.getSelectionModel().getSelectedIndex() == -1) {
            Platform.runLater(() -> MessageUtil.alert(Alert.AlertType.ERROR, "输出格式", null, "请选择输出格式！"));
            return false;
        }
        if (viewHolder.outputDirectory.getText().isEmpty()) {
            // 输出文件夹为空
            Platform.runLater(() -> MessageUtil.alert(Alert.AlertType.ERROR, "输出文件夹", null, "输出文件夹不能为空！"));
            return false;
        }
        return true;
    }

    private boolean aMapKeys() {
        appendMessage("读取高德key中");
        Queue<String> aMapKeys = parseKeyText(viewHolder.keys.getText());
        if (aMapKeys == null) {
            // key解析异常
            Platform.runLater(() -> MessageUtil.alert(Alert.AlertType.ERROR, "高德key", null, "请检查key的格式！"));
            return false;
        }
        configHolder.aMapKeys = aMapKeys;
        appendMessage("高德key读取成功");
        return true;
    }

    private boolean threadNum() {
        appendMessage("读取线程数目中");
        Integer threadNum = ParseUtil.parseStr2Int(viewHolder.threadNum.getText());
        if (threadNum == null) {
            appendMessage("解析线程数目失败，请检查格式！");
            analysis(false);
            return false;
        }
        appendMessage("线程数目读取成功");
        configHolder.threadNum = threadNum;
        return true;
    }

    private boolean threshold() {
        appendMessage("读取阈值中");
        Integer threshold = ParseUtil.parseStr2Int(viewHolder.threshold.getText());
        if (threshold == null) {
            appendMessage("解析阈值失败，请检查！");
            analysis(false);
            return false;
        }
        appendMessage("阈值读取成功");
        configHolder.threshold = threshold;
        return true;
    }

    private boolean keywords() {
        appendMessage("读取POI关键字中");
        StringBuilder keywords = new StringBuilder();
        String[] keywordArr = viewHolder.keywords.getText().split(",");
        for (int i = 0; i < keywordArr.length; i++) {
            keywords.append(keywordArr[i]);
            if (i != keywordArr.length - 1)
                keywords.append("|");
        }
        appendMessage("POI关键字读取成功");
        configHolder.keywords = keywords.toString();
        return true;
    }

    private boolean types() {
        appendMessage("读取POI类型中");
        StringBuilder types = new StringBuilder();
        String[] typeArr = viewHolder.types.getText().split(",");
        for (int i = 0; i < typeArr.length; i++) {
            types.append(typeArr[i]);
            if (i != typeArr.length - 1)
                types.append("|");
        }
        appendMessage("POI类型读取成功");
        configHolder.types = types.toString();
        return true;
    }

    private boolean qps() {
        // 读取开发者类型
        int qps = 0;
        appendMessage("您是" + viewHolder.userType.getValue());
        switch (viewHolder.userType.getValue()) {
            case IndividualDevelopers:
                qps = 20;
                break;
            case IndividualCertifiedDeveloper:
                qps = 50;
                break;
            case EnterpriseDeveloper:
                qps = 300;
                break;
        }
        configHolder.qps = qps;
        return true;
    }

    private boolean tab() {
        String tab = viewHolder.tabs.getSelectionModel().getSelectedItem().getText();
        switch (tab) {
            case "行政区":
                if (viewHolder.adCode.getText().isEmpty()) {
                    // 行政区为空
                    Platform.runLater(() -> MessageUtil.alert(Alert.AlertType.ERROR, "行政区代码", null, "请设置行政区代码！"));
                    return false;
                }
                break;
            case "矩形":
                if (viewHolder.rectangle.getText().isEmpty()) {
                    // 矩形范围为空
                    Platform.runLater(() -> MessageUtil.alert(Alert.AlertType.ERROR, "矩形", null, "请设置矩形范围！"));
                    return false;
                }
                // 矩形格式错误
                if (!parseRect(viewHolder.rectangle.getText())) {
                    Platform.runLater(() -> MessageUtil.alert(Alert.AlertType.ERROR, "矩形", null, "请检查矩形输入格式！\n 正确格式：114.12,30.53#115.28,29.59"));
                    return false;
                }
                break;
            case "自定义":
                if (viewHolder.userFile.getText().isEmpty()) {
                    // 行政区为空
                    Platform.runLater(() -> MessageUtil.alert(Alert.AlertType.ERROR, "自定义", null, "请设置geojson文件路径！"));
                    return false;
                }
                break;
        }
        configHolder.tab = tab;
        return true;
    }

    private void extension() {
        for (POI.OutputFields item : viewHolder.outputFields) {
            if (item.inExtension()) {
                configHolder.extension = "all";
                return;
            }
        }
        configHolder.extension = "base";
    }

    private void alterThreadNum() {
        if (configHolder.threadNum > configHolder.qps * configHolder.aMapKeys.size()) {
            int maxThreadNum = getMaxThreadNum(configHolder.qps, configHolder.aMapKeys.size());
            appendMessage(viewHolder.userType.getValue() + "线程数不能超过" + maxThreadNum);
            configHolder.threadNum = maxThreadNum;
            appendMessage("设置线程数目为" + maxThreadNum);
        }
    }

    public int getMaxThreadNum(int qps, int keyNum) {
        return qps * keyNum;
    }

    /**
     * 根据线程数、key 数目和 QPS 设置每次运行时间
     * <p>
     * threadNum: 线程数目
     * <p>
     * qps:       用户 qps
     * <p>
     * keysNum:   key 数量
     * <p>
     * 每次运行时间 ms
     */
    private void perExecuteTime() {
        int threadNum = configHolder.threadNum, qps = configHolder.qps, keysNum = configHolder.aMapKeys.size();
        configHolder.perExecuteTime = getPerExecuteTime(keysNum, threadNum, qps, 1);
    }

    private int getPerExecuteTime(int keysNum, int threadNum, int qps, double waitFactor) {
        return (int) ((1000 * (threadNum * 1.0 / (qps * keysNum))) * waitFactor);
    }

    public static Double[] getBoundaryFromGeometry(Geometry geometry) {
        Envelope envelopeInternal = geometry.getEnvelopeInternal();

        double left = envelopeInternal.getMinX(), bottom = envelopeInternal.getMinY(),
                right = envelopeInternal.getMaxX(), top = envelopeInternal.getMaxY();
        return new Double[]{left, bottom, right, top};
    }

    public void execute(Task task) {
        clearMessage();
        if (!check()) return;
        if (!aMapKeys()) return;
        if (!threadNum()) return;
        if (!threshold()) return;
        if (!keywords()) return;
        if (!types()) return;
        if (!qps()) return;
        if (!tab()) return;
        extension();
        alterThreadNum();
        perExecuteTime();
        otherParams();
        analysis(true);

        worker = Executors.newSingleThreadExecutor();
        if (task == null) {
            String boundaryConfig = "";
            switch (configHolder.tab) {
                case "行政区":
                    if (!configHolder.hasStart) return;
                    String adCode = viewHolder.adCode.getText();
                    appendMessage("获取行政区 " + adCode + " 区域边界中");
                    // 获取完整边界和边界名
                    Map<String, Object> data = DataVUtil.getBoundaryAndAdNameByAdCodeFromDataV(adCode);
                    if (data == null) {
                        if (!configHolder.hasStart) return;
                        Platform.runLater(() -> MessageUtil.alert(Alert.AlertType.ERROR, "行政区边界", null, "无法获取行政区边界，请检查行政区代码或稍后重试！"));
                        analysis(false);
                        return;
                    }
                    String adName = (String) data.get("adName");
                    Geometry geometry = (Geometry) data.get("gcj02Boundary");
                    configHolder.boundary = getBoundaryFromGeometry(geometry);
                    if (!configHolder.hasStart) return;
                    appendMessage("成功获取行政区 " + adCode + ":" + adName + " 区域边界");
                    boundaryConfig = configHolder.tab + ":" + adCode + "," + adName;
                    break;
                case "矩形":
                    // 获取坐标类型
                    if (!configHolder.hasStart) return;
                    String rectangle = viewHolder.rectangle.getText();
                    CoordinateType type = viewHolder.rectangleCoordinateType.getValue();
                    boundaryConfig = configHolder.tab + ":" + rectangle + "," + type;
                    appendMessage("解析矩形区域中");
                    Double[] rectangleBoundary = BoundaryUtil.getBoundaryByRectangle(rectangle, type);
                    if (rectangleBoundary == null) {
                        if (!configHolder.hasStart) return;
                        Platform.runLater(() -> MessageUtil.alert(Alert.AlertType.ERROR, "矩形", null, "无法获取矩形边界，请检查矩形格式！"));
                        analysis(false);
                        return;
                    }
                    configHolder.boundary = rectangleBoundary;
                    if (!configHolder.hasStart) return;
                    appendMessage("解析矩形区域成功");
                    break;
                case "自定义":
                    if (!configHolder.hasStart) return;
                    appendMessage("解析用户geojson文件中");
                    Geometry boundary;
                    try {
                        boundary = BoundaryUtil.getBoundaryByUserFile(viewHolder.userFile.getText(), viewHolder.userFileCoordinateType.getValue());
                    } catch (IOException e) {
                        e.printStackTrace();
                        if (!configHolder.hasStart) return;
                        Platform.runLater(() -> MessageUtil.alert(Alert.AlertType.ERROR, "自定义", null, "geojson文件解析失败：" + e.getMessage()));
                        analysis(false);
                        return;
                    }
                    configHolder.boundary = getBoundaryFromGeometry(boundary);

                    boundaryConfig = configHolder.tab + ":" + viewHolder.userFile.getText() + ","
                            + viewHolder.userFileCoordinateType.getValue().description();
                    if (!configHolder.hasStart) return;
                    appendMessage("成功解析用户文件");
                    break;
            }
            try {
                BoundaryType boundaryType = BoundaryType.getBoundaryType(boundaryConfig.split(":")[0]);
                task = Task.builder()
                        .aMapKeys(configHolder.aMapKeys)
                        .types(configHolder.types)
                        .keywords(configHolder.keywords)
                        .threadNum(configHolder.threadNum)
                        .threshold(configHolder.threshold)
                        .outputDirectory(viewHolder.outputDirectory.getText())
                        .outputType(viewHolder.format.getValue())
                        .userType(viewHolder.userType.getValue())
                        .requestExpectedTimes(0)
                        .requestActualTimes(0)
                        .poiActualCount(0)
                        .poiExpectedCount(0)
                        .totalExecutedTimes(0)
                        .taskStatus(TaskStatus.UnStarted)
                        .boundaryConfig(boundaryConfig)
                        .boundary(configHolder.boundary)
                        .boundaryType(boundaryType)
                        .jobs(new ArrayList<>())
                        .filter(TaskUtil.generateFilter(boundaryConfig, boundaryType))
                        .build();
                TaskPo taskPo = task.toTaskPo();
                taskService.save(taskPo);
                // 自增 id
                task = taskPo.toTask();
            } catch (IOException e) {
                e.printStackTrace();
                Platform.runLater(() -> MessageUtil.alert(Alert.AlertType.ERROR, "自定义", null, "task构建失败：" + e.getMessage()));
                return;
            }
        }

        Task finalTask = task;
        worker.execute(() -> {
            executorService = Executors.newFixedThreadPool(finalTask.threadNum());
            completionService = new ExecutorCompletionService<>(executorService);
            executeTask(finalTask);
            analysis(false);
        });
    }


    public void cancel() {
        analysis(false);
    }

    private void analysis(boolean start) {
        if (!configHolder.hasStart && !start) {
            return;
        }
        if (configHolder.hasStart && start) {
            return;
        }
        configHolder.hasStart = start;
        setDisable(start);
        appendMessage(start ? "开始POI爬取，请勿操作" : "停止POI爬取");
        if (!start && executorService != null)
            executorService.shutdownNow();
        if (!start && worker != null)
            worker.shutdownNow();
    }

    private void setDisable(boolean isAnalysis) {
        Platform.runLater(() -> {
            viewHolder.execute.setDisable(isAnalysis);
            viewHolder.threadNum.setDisable(isAnalysis);
            viewHolder.keys.setDisable(isAnalysis);
            viewHolder.keywords.setDisable(isAnalysis);
            viewHolder.types.setDisable(isAnalysis);
            viewHolder.tabs.setDisable(isAnalysis);
            viewHolder.threshold.setDisable(isAnalysis);
            viewHolder.format.setDisable(isAnalysis);
            viewHolder.outputDirectory.setDisable(isAnalysis);
            viewHolder.directoryBtn.setDisable(isAnalysis);
            viewHolder.poiType.setDisable(isAnalysis);
            viewHolder.userType.setDisable(isAnalysis);
            viewHolder.poiCateBig.setDisable(isAnalysis);
            viewHolder.poiCateMid.setDisable(isAnalysis);
            viewHolder.poiCateSub.setDisable(isAnalysis);
            viewHolder.poiCateAddBtn.setDisable(isAnalysis);
            viewHolder.chooseOutputFieldsBtn.setDisable(isAnalysis);
            viewHolder.openQPSBtn.setDisable(isAnalysis);
        });
    }

    private Queue<String> parseKeyText(String keys) {
        List<String> keyList = Arrays.asList(keys.split(","));
        String pattern = "^[A-Za-z0-9]+$";
        for (String key : keyList) {
            boolean isMatch = Pattern.matches(pattern, key);
            if (!isMatch) {
                return null;
            }
        }
        return new ArrayDeque<>(keyList);
    }

    private boolean continueLargeTaskByDialog(int jobSize, int hintCount) {
        if (jobSize < hintCount) {
            return true;
        }
        final FutureTask<Boolean> query = new FutureTask<>(() ->
                MessageUtil.alertConfirmationDialog(
                        "任务量提示",
                        "任务量过大，请选择是否继续进行爬取",
                        "该Task需要至少执行" + jobSize + "次请求，如想继续爬取，请点击确认，否则请点击取消",
                        "继续",
                        "放弃"));

        Platform.runLater(query);
        try {
            // 阻塞本线程
            return query.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 执行爬取任务
     *
     * @param task task对象
     */
    private void executeTask(Task task) {
        configHolder.waitFactorForQps = 0;
        if (TaskStatus.UnStarted.equals(task.taskStatus()) || TaskStatus.Preprocessing.equals(task.taskStatus())) {
            // 清空数据表
            jobService.clearTable();
            poiService.clearTable();
            task.taskStatus(TaskStatus.Preprocessing);
            taskService.updateById(task.toTaskPo());
            // 1. 获取所有任务网格的第一页
            appendMessage("划分所有任务网格中");
            List<Job> firstPageJobs;
            try {
                firstPageJobs = getAnalysisGridsReTry(task.boundary(), task, 3);
            } catch (NoTryAgainException e) {
                // e.printStackTrace();
                if (configHolder.hasStart) appendMessage(e.getMessage());
                return;
            }
            task.jobs().addAll(firstPageJobs);
            appendMessage("任务网格切分成功，共有" + firstPageJobs.size() + "个任务网格");

            // 2. 生成第二页之后的的Job
            List<Job> jobsAfterSecondPage = generateJobsAfterSecondPage(firstPageJobs);
            task.jobs().addAll(jobsAfterSecondPage);

            appendMessage("任务构建成功，共有" + task.jobs().size() + "个任务，还有" + jobsAfterSecondPage.size() + "个任务等待完成");
            int requestLeastCount = jobsAfterSecondPage.size();
            if (!continueLargeTaskByDialog(requestLeastCount, 5000)) {
                analysis(false);
                return;
            }
            // 保存Task
            task.taskStatus(TaskStatus.Processing);
            taskService.updateById(task.toTaskPo());
        }

        // 3. 开始爬取
        if (!configHolder.hasStart) return;
        appendMessage("开始POI爬取，" + (!task.keywords().isEmpty() ? "POI关键字：" + task.keywords() : "") + (!task.types().isEmpty() ? (" POI类型：" + task.types()) : ""));

        List<POI.Info> pois;
        try {
            pois = getPoiOfJobsWithReTry(task, 3);
        } catch (NoTryAgainException e) {
            // e.printStackTrace();
            if (configHolder.hasStart) appendMessage(e.getMessage());
            return;
        }

//        appendMessage("执行过滤算法中");

        pois = pois.stream().filter(task.filter()).collect(Collectors.toList());
//        appendMessage("过滤成功，共获得POI：" + pois.size() + "条");
        appendMessage("该区域边界共含POI：" + pois.size() + "条");

        // 导出res
        switch (viewHolder.format.getValue()) {
            case CSV:
                writeToCsv(pois, viewHolder.outputFields);
                break;
            case GEOJSON:
                writeToGeoJson(pois, viewHolder.outputFields);
                break;
            case SHAPEFILE:
                writeToShp(pois, viewHolder.outputFields);
                break;
            case SQLSCRIPT:
                writeSqlScript(pois, viewHolder.outputFields);
                break;
        }
        taskService.updateById(task.toTaskPo());

        int allJobSize = jobService.count();
        int unFinishJobSize = jobService.countUnFinished();
        List<POI.Info> finalPois = pois;
        Platform.runLater(() -> MessageUtil.alert(
                Alert.AlertType.INFORMATION,
                "结果分析",
                "任务结果分析",
                "poi爬取任务结果如下：\n" +
                        "任务状态：" + task.taskStatus().description() + "\n" +
                        "任务状态：" + task.taskStatus().description() + "\n" +
                        "完成度：" + (allJobSize - unFinishJobSize) + "/" + allJobSize + " \n" +
                        "总计爬取poi数量：" + finalPois.size() + "\n"
        ));
    }

    private List<Job> getAnalysisGridsReTry(Double[] beginRect, Task task, int tryTimes) throws NoTryAgainException {
        Job beginJob = new Job(null, task.id(), beginRect, task.types(), task.keywords(), 1, configHolder.SIZE);
        ArrayList<Job> falseJobs = new ArrayList<>();
        List<Job> analysisGrids = getAnalysisGrids(Collections.singletonList(beginJob), task, 0, falseJobs);
        int i = 1;
        while (falseJobs.size() != 0) {
            appendMessage("正在重试：第" + i + "次");
            ArrayList<Job> newTryJobs = new ArrayList<>(falseJobs);
            falseJobs.clear();
            analysisGrids.addAll(getAnalysisGrids(newTryJobs, task, analysisGrids.size(), falseJobs));
            if (i == tryTimes) {
                appendMessage("已重试三次" + "重试失败，还有" + falseJobs.size() + "个Job未切分");
                appendMessage("请重新点击执行，尝试爬取，或放弃尝试");
                throw new NoTryAgainException(NoTryAgainErrorCode.STOP_TASK);
            }
            i++;
        }
        int requestTimesForPreProcessing = task.requestActualTimes() - analysisGrids.size();
        appendMessage("用于额外探测的请求有 " + requestTimesForPreProcessing + " 次");
        task.plusRequestExceptedTimes(requestTimesForPreProcessing);
        return analysisGrids;
    }

    /**
     * 爬取第一页生成poi爬取格网，每个格网的数据量小于阈值。
     *
     * @param tryJobs 初始尝试的Job
     * @param task    task对象
     * @return 划分格网的第一页Job
     */
    private List<Job> getAnalysisGrids(List<Job> tryJobs, Task task, int baseJobCount, ArrayList<Job> falseJobs) throws NoTryAgainException {
        ExecutorService executorService = Executors.newFixedThreadPool(task.threadNum());
        List<Job> analysisGrid = new ArrayList<>();
        CompletionService<Job> completionService = new ExecutorCompletionService<>(executorService);

        List<Job> nextTryJobs = new ArrayList<>();

        while (tryJobs.size() != 0) {
            List<Job> unTriedJobs = new ArrayList<>(tryJobs);
            for (Job job : tryJobs) {
                if (!configHolder.hasStart) {
                    throw new NoTryAgainException(NoTryAgainErrorCode.STOP_TASK);
                }
                completionService.submit(() -> {
                    try {
                        executeJob(job);
                        return job;
                    } catch (TryAgainException e) {
                        // 执行完job对爬取结果的处理
                        // 如果主动停止，则不输出
                        synchronized (this) {
                            appendMessage(e.getMessage());
                            job.jobStatus(JobStatus.Failed);
                            job.tryAgainErrorCode(e.tryAgainError());
                            return job;
                        }
                    } catch (NoTryAgainException e) {
                        // 执行完job对爬取结果的处理
                        // 如果主动停止，则不输出
                        synchronized (this) {
                            appendMessage(e.getMessage());
                            // 暂定本次爬取
                            analysis(false);
                            job.jobStatus(JobStatus.Failed);
                            job.noTryAgainErrorCode(e.noTryAgainError());
                            return job;
                        }
                    }
                });
            }

            int tryTimes = (int) (20 / 0.5);
            for (int i = 0; i < tryJobs.size(); i++) {
                for (int j = 0; j < tryTimes; j++) {
                    Future<Job> future;
                    try {
                        future = completionService.poll(500, TimeUnit.MILLISECONDS);
                        if (future != null) {
                            task.plusRequestActualTimes(); //增加请求次数
                            Job job = future.get();
                            unTriedJobs.remove(job);
                            if (job.jobStatus() != JobStatus.SUCCESS) {
                                if (job.noTryAgainErrorCode() != null) {
                                    throw new NoTryAgainException(job.noTryAgainErrorCode());
                                }
                                falseJobs.add(job);
                                break;
                            }
                            if (job.poi().count() > task.threshold()) {
                                appendMessage("超出阈值，继续四分，已包含" + (analysisGrid.size() + baseJobCount) + "个任务");
                                // 继续四分
                                Double left = job.bounds()[0], bottom = job.bounds()[1], right = job.bounds()[2], top = job.bounds()[3];
                                double itemWidth = (right - left) / 2;
                                double itemHeight = (top - bottom) / 2;
                                for (int m = 0; m < 2; m++) {
                                    for (int n = 0; n < 2; n++) {
                                        Double[] bounds = {left + m * itemWidth, bottom + n * itemHeight,
                                                left + (m + 1) * itemWidth, bottom + (n + 1) * itemHeight};
                                        nextTryJobs.add(new Job(null, task.id(), bounds, task.types(), task.keywords(), 1, configHolder.SIZE));
                                    }
                                }
                            } else {
                                analysisGrid.add(job);  // new double[]{left, bottom, right, top});
                                statistics(job, task);
                                appendMessage("已包含" + (analysisGrid.size() + baseJobCount) + "个任务");
                            }
                            break;
                        }
                        if ((j + 1) == tryTimes) {
                            throw new TimeoutException();
                        }
                    } catch (TimeoutException e) {
//                        e.printStackTrace();
                        falseJobs.addAll(unTriedJobs);
                    } catch (InterruptedException | ExecutionException e) {
//                        e.printStackTrace();
                        throw new NoTryAgainException(NoTryAgainErrorCode.STOP_TASK, e);
                    }
                }
            }
            tryJobs = nextTryJobs;
            nextTryJobs = new ArrayList<>();
        }

        // 统计请求相关参数
        for (Job job : analysisGrid) {
            POI poi = job.poi();
            task.plusPoiExceptedSum(poi.count());
            task.plusRequestExceptedTimes((int) Math.ceil(poi.count() * 1.0 / job.size()));
            job.poiExpectedCount(Math.min(poi.count(), job.size()));
        }

        // 保存第一页的数据
        taskService.updateById(task.toTaskPo());
        jobService.saveBatch(BeanUtils.jobs2JobPos(analysisGrid));
        poiService.saveBatch(BeanUtils.jobs2PoiPos(analysisGrid, configHolder.extension.equals("all")));
        return analysisGrid;
    }

    /**
     * 生成第二页往后的Job
     *
     * @param analysisGrid 存储job的容器
     * @return 生成的Job
     */
    private List<Job> generateJobsAfterSecondPage(List<Job> analysisGrid) {
        List<Job> jobs = new ArrayList<>();
        for (Job firstPageJob : analysisGrid) {
            int total = firstPageJob.poi().count();
            int size = firstPageJob.size();
            int taskNum = (int) Math.ceil(total * 1.0 / size);
            for (int page = 2; page <= taskNum; page++) {
                Job job = new Job(null, firstPageJob.taskId(), firstPageJob.bounds(), firstPageJob.types(), firstPageJob.keywords(), page, firstPageJob.size());
                job.poiExpectedCount(page == taskNum ? total - size * (taskNum - 1) : size);
                jobs.add(job);
            }
        }
        // 保存未爬取的其他job
        jobService.saveBatch(BeanUtils.jobs2JobPos(jobs));
        return jobs;
    }

    /**
     * 重试，默认重试三次
     *
     * @param task       task对象
     * @param retryTimes 重试次数
     * @return 爬到的poi数据
     */
    private List<POI.Info> getPoiOfJobsWithReTry(Task task, int retryTimes) throws NoTryAgainException {
        List<Job> jobs = Collections.unmodifiableList(BeanUtils.jobPos2Jobs((jobService.listUnFinished())));
        int jobCount = jobService.count();
        int i = 0;
        while (configHolder.hasStart) {
            if (i != 0) {
                appendMessage("正在重试：第" + i + "次");
            }
            configHolder.haveSavedUnfinishedJobs = false;
            spiderPoiOfJobs(jobs, task, jobCount);
            if (!configHolder.hasStart) {
                return BeanUtils.poiPo2PoiInfo(poiService.list(), configHolder.extension.equals("all"));
            }
            List<Job> newJobs = Collections.unmodifiableList(BeanUtils.jobPos2Jobs(jobService.listUnFinished()));
            if (newJobs.size() == 0) {
                task.taskStatus(TaskStatus.Success);
                break;
            }
            appendMessage((i == 0 ? "初次爬取结果" : "第" + i + "次重试结果") + "：总计" + jobCount + "个任务，其中已完成" + (jobCount - newJobs.size()) + "个，失败任务" + newJobs.size() + "个");
            if (i == retryTimes) {
                List<JobPo> unFinishedJobs = jobService.listUnFinished();
                appendMessage("已重试三次" + "重试失败，还有" + unFinishedJobs.size() + "个Job未爬取");
                appendMessage("请重新点击执行，尝试爬取，或放弃尝试");
                task.taskStatus(TaskStatus.Some_Failed);
                break;
            }
            jobs = newJobs;
            i++;
        }
        return BeanUtils.poiPo2PoiInfo(poiService.list(), configHolder.extension.equals("all"));
    }

    /**
     * 构造异步任务，并行爬取
     *
     * @param unFinishedJobs 待爬取的job
     * @param task           task对象
     */
    private void spiderPoiOfJobs(List<Job> unFinishedJobs, Task task, int allJobsCount) throws NoTryAgainException {
        int finishedJobsCount = allJobsCount - unFinishedJobs.size();
        // 缓存机制
        int saveThreshold = 50;
        List<Job> cached = new ArrayList<>();
        ArrayList<Job> unFinishedJob = new ArrayList<>(unFinishedJobs);

        // 构造异步job
        for (Job job : unFinishedJobs) {
            completionService.submit(() -> {
                try {
                    executeJob(job);
                    return job;
                } catch (TryAgainException e) {
                    synchronized (this) {
                        // 执行完job对爬取结果的处理
                        // 如果主动停止，则不输出
                        if (configHolder.hasStart) appendMessage(e.getMessage());
                        job.jobStatus(JobStatus.Failed);
                        job.tryAgainErrorCode(e.tryAgainError());
                        return job;
                    }
                } catch (NoTryAgainException e) {
                    synchronized (this) {
                        // 执行完job对爬取结果的处理
                        // 如果主动停止，则不输出
                        if (configHolder.hasStart) appendMessage(e.getMessage());
                        // 暂定本次爬取
                        analysis(false);
                        job.jobStatus(JobStatus.Failed);
                        job.noTryAgainErrorCode(e.noTryAgainError());
                        return job;
                    }
                }
            });
        }

        // 阻塞获取
        try {
            int tryTimes = (int) (20 / 0.5);
            for (int i = 0; i < unFinishedJobs.size(); i++) {
                // 执行一个爬取job
                for (int j = 0; j < tryTimes; j++) {
                    Future<Job> future = completionService.poll(500, TimeUnit.MILLISECONDS);
                    if (future != null) {
                        Job job = future.get();
                        if (job.jobStatus() != JobStatus.SUCCESS) {
                            if (job.noTryAgainErrorCode() != null) {
                                throw new NoTryAgainException(job.noTryAgainErrorCode());
                            }
                        } else {
                            statistics(job, task);
                            appendMessage("已执行任务：" + (finishedJobsCount + i + 1) + "/" + allJobsCount);
                        }
                        cached.add(job);
                        unFinishedJob.remove(job);
                        break;
                    }
                    if ((j + 1) == tryTimes) {
                        throw new TimeoutException();
                    }
                }
                if ((i + 1) % saveThreshold == 0 || (i + 1) == unFinishedJobs.size()) {
                    appendMessage("正在写入数据，请稍等...");
                    taskService.updateById(task.toTaskPo());
                    jobService.updateBatch(BeanUtils.jobs2JobPos(cached));
                    poiService.saveBatch(BeanUtils.jobs2PoiPos(
                            cached.stream()
                                    .filter(job -> job.jobStatus().equals(JobStatus.SUCCESS))
                                    .collect(Collectors.toList()), configHolder.extension.equals("all")));
                    cached.clear();
                }
            }
        } catch (TimeoutException e) {
//            e.printStackTrace();
            saveUnFinishedJob(task, cached, unFinishedJob);
        } catch (NoTryAgainException | InterruptedException | ExecutionException e) {
//            e.printStackTrace();
            saveUnFinishedJob(task, cached, unFinishedJob);
            throw new NoTryAgainException(NoTryAgainErrorCode.STOP_TASK, e.getMessage());

        }
    }

    /**
     * 保存未完成的Jobs
     *
     * @param task          task对象
     * @param cached        缓存容器
     * @param unFinishedJob 未完成的Job
     */
    private synchronized void saveUnFinishedJob(Task task, List<Job> cached, ArrayList<Job> unFinishedJob) {
        if (configHolder.haveSavedUnfinishedJobs) {
            return;
        }
        appendMessage("任务即将停止，正在保存任务状态...请不要关闭软件");
        for (Job unJob : unFinishedJob) {
            unJob.jobStatus(JobStatus.Failed);
            cached.add(unJob);
        }
        taskService.updateById(task.toTaskPo());
        jobService.updateBatch(BeanUtils.jobs2JobPos(cached));
        poiService.saveBatch(BeanUtils.jobs2PoiPos(
                cached.stream()
                        .filter(job -> job.jobStatus().equals(JobStatus.SUCCESS))
                        .collect(Collectors.toList()), configHolder.extension.equals("all"))
        );

        task.taskStatus(TaskStatus.Pause);
        taskService.updateById(task.toTaskPo());
        configHolder.haveSavedUnfinishedJobs = true;
    }

    /**
     * 执行一个Job
     *
     * @param job 等待执行的job
     * @throws TryAgainException 如果爬取失败，抛出该异常
     */
    private void executeJob(Job job) throws NoTryAgainException, TryAgainException {
        double left = job.bounds()[0], bottom = job.bounds()[1], right = job.bounds()[2], top = job.bounds()[3];
        String polygon = left + "," + top + "|" + right + "," + bottom;
        String key = getAMapKey();
        job.poi(getPoi(key, polygon, job.keywords(), job.types(), job.page(), job.size()));
        job.jobStatus(JobStatus.SUCCESS); // 设置执行状态为Success
    }

    /**
     * 获取单个Key，每个key的均匀使用
     *
     * @return 选定的key值
     * @throws NoTryAgainException 如果未获取到key（例如没有可选key），抛出该异常
     */
    private synchronized String getAMapKey() throws NoTryAgainException {
        if (configHolder.aMapKeys.isEmpty()) {
            return null;
        }
        String key = configHolder.aMapKeys.poll();
        if (key == null) {
            throw new NoTryAgainException(NoTryAgainErrorCode.KEY_POOL_RUN_OUT_OF);
        }
        configHolder.aMapKeys.offer(key);
        return key;
    }

    private synchronized boolean removeKey(String key) {
        return configHolder.aMapKeys.remove(key);
    }

    /**
     * 统计相关指标
     *
     * @param job  被统计的job对象
     * @param task task对象
     */
    private void statistics(Job job, Task task) {
        POI poi = job.poi();
        // 如果getPoi抛出异常，则后续代码不进行
        // 统计Job的相关数据
        // 设置Job的实际统计量
        job.plusRequestActualTimes(); // 增加执行次数
        job.plusToPoiActualCount(poi.details().size()); // 增加实际获取的poi数量

        // 统计Task的相关数据
        // 设置task的实际统计量
        task.plusRequestActualTimes(); // 增加执行次数
        task.plusPoiActualSum(poi.details().size());
    }

    /**
     * 爬取单个Job的Poi
     *
     * @param key      key
     * @param polygon  矩形范围
     * @param keywords 关键字
     * @param types    类型
     * @param page     页数
     * @param size     单页数据量
     * @return 爬取到的poi对象
     * @throws TryAgainException 如果爬取失败，则抛出该异常
     */
    private POI getPoi(String key, String polygon, String keywords, String types, int page, int size) throws NoTryAgainException, TryAgainException {
        if (!configHolder.hasStart) {
            throw new NoTryAgainException(NoTryAgainErrorCode.STOP_TASK);
        }
        LocalDateTime startTime = LocalDateTime.now();//获取开始时间
        POI poi = mapDao.getPoi(key, polygon, keywords, types, configHolder.extension, page, size);
        LocalDateTime endTime = LocalDateTime.now(); //获取结束时间
        int maxThreadNum = getMaxThreadNum(configHolder.qps, configHolder.aMapKeys.size());
        int threadNum = Math.min(maxThreadNum, configHolder.threadNum);
        int perExecuteTime = getPerExecuteTime(configHolder.aMapKeys.size(), threadNum, configHolder.qps, configHolder.threadNum * 1.0 / threadNum);
        if (Duration.between(startTime, endTime).toMillis() < perExecuteTime) { // 严格控制每次执行perExecuteTime
            try {
                TimeUnit.MILLISECONDS.sleep(perExecuteTime - Duration.between(startTime, endTime).toMillis() + (long) (Math.log(configHolder.waitFactorForQps) * 50L));
            } catch (InterruptedException e) {
//                e.printStackTrace();
            }
        }

        // 异常情况处理
        if (poi == null || poi.infoCode() != 10000) {
            if (poi == null || poi.infoCode() == null || poi.status() == null || poi.info() == null) {
                // 如果必要字段为空
                throw new TryAgainException(TryAgainErrorCode.RETURN_NULL_DATA);
            } else {
                // 不可重试异常
                NoTryAgainErrorCode noTryAgainErrorCode = NoTryAgainErrorCode.getError(poi.infoCode());
                if (noTryAgainErrorCode != null) {
                    synchronized (this) {
                        // key额度用完，移除该key
                        if (noTryAgainErrorCode.equals(NoTryAgainErrorCode.USER_DAILY_QUERY_OVER_LIMIT)) {
                            NoTryAgainException noTryAgainException = new NoTryAgainException(NoTryAgainErrorCode.USER_DAILY_QUERY_OVER_LIMIT);
                            if (configHolder.aMapKeys.contains(key)) {
                                // 输出底层错误信息
                                appendMessage(noTryAgainException.getMessage());
                                removeKey(key);
                            }
                            // 如果还有可用key，则继续尝试，否则抛出不可重试异常
                            if (configHolder.aMapKeys.size() != 0) {
                                throw new TryAgainException(TryAgainErrorCode.TRY_OTHER_KEY, "无效key：" + key, noTryAgainException);
                            }
                        }
                    }
                    throw new NoTryAgainException(noTryAgainErrorCode);
                }
                // 可重试异常
                TryAgainErrorCode tryAgainErrorCode = TryAgainErrorCode.getError(poi.infoCode());
                if (tryAgainErrorCode != null) {
                    if (configHolder.errorCodeHashSet.contains(tryAgainErrorCode)) {
                        configHolder.waitFactorForQps++;
                    }
                    throw new TryAgainException(tryAgainErrorCode);
                }
                throw new NoTryAgainException(NoTryAgainErrorCode.UNKNOWN_WEB_ERROR);
            }
        }
        if (poi.count() == null || poi.details() == null || poi.infoCode() == null || poi.status() == null) {
            throw new TryAgainException(TryAgainErrorCode.RETURN_NULL_DATA);
        }
        return poi;
    }

    private String filename(String format) {
        String filename = viewHolder.outputDirectory.getText();
        switch (configHolder.tab) {
            case "行政区":
                filename = filename + "/解析结果_" + viewHolder.adCode.getText() + (!viewHolder.types.getText().isEmpty() ? "types_" + viewHolder.types.getText() : "") + (!viewHolder.keywords.getText().isEmpty() ? "keywords_" + viewHolder.keywords.getText() : "");
                break;
            case "矩形":
                filename = filename + "/解析结果_" + viewHolder.rectangle.getText() + (!viewHolder.types.getText().isEmpty() ? "types_" + viewHolder.types.getText() : "") + (!viewHolder.keywords.getText().isEmpty() ? "keywords_" + viewHolder.keywords.getText() : "");
                break;
            case "自定义":
                filename = filename + "/解析结果_" + FileUtil.getFileName(viewHolder.userFile.getText()) + (!viewHolder.types.getText().isEmpty() ? "types_" + viewHolder.types.getText() : "") + (!viewHolder.keywords.getText().isEmpty() ? "keywords_" + viewHolder.keywords.getText() : "");
                break;
        }
        return filename.length() > 200 ? filename.substring(0, 200) + "等." + format : filename + "." + format;
    }

    private String[] header(List<POI.OutputFields> outputFields) {
        return outputFields.stream()
                .map(POI.OutputFields::fieldName)
                .toArray(String[]::new);
    }

    private String[] content(POI.Info item, List<POI.OutputFields> outputFields) {
        List<String> result = new ArrayList<>();
        String[] lnglat = BeanUtils.obj2String(item.location()).split(",");
        double[] wgs84;
        if (lnglat.length == 2) {
            wgs84 = CoordinateTransformUtil.transformGCJ02ToWGS84(Double.parseDouble(lnglat[0]), Double.parseDouble(lnglat[1]));
        } else {
            wgs84 = new double[]{0.0, 0.0};
        }

        for (POI.OutputFields field : outputFields) {
            switch (field) {
                case ID:
                    result.add(BeanUtils.obj2String(item.poiId()));
                    break;
                case TEL:
                    result.add(BeanUtils.obj2String(item.tel()));
                    break;
                case NAME:
                    result.add(BeanUtils.obj2String(item.name()));
                    break;
                case TYPE:
                    result.add(BeanUtils.obj2String(item.type()));
                    break;
                case EMAIL:
                    result.add(BeanUtils.obj2String(item.email()));
                    break;
                case AD_CODE:
                    result.add(BeanUtils.obj2String(item.adCode()));
                    break;
                case AD_NAME:
                    result.add(BeanUtils.obj2String(item.adName()));
                    break;
                case ADDRESS:
                    result.add(BeanUtils.obj2String(item.address()));
                    break;
                case WEBSITE:
                    result.add(BeanUtils.obj2String(item.website()));
                    break;
                case BIZ_TYPE:
                    result.add(BeanUtils.obj2String(item.bizType()));
                    break;
                case GCJ02_LNG:
                    if (lnglat.length == 2) {
                        result.add(lnglat[0]);
                    }
                    break;
                case GCJ02_LAT:
                    if (lnglat.length == 2) {
                        result.add(lnglat[1]);
                    }
                    break;
                case WGS84_LNG:
                    result.add(BeanUtils.obj2String(wgs84[0]));
                    break;
                case WGS84_LAT:
                    result.add(BeanUtils.obj2String(wgs84[1]));
                    break;
                case CITY_CODE:
                    result.add(BeanUtils.obj2String(item.cityCode()));
                    break;
                case CITY_NAME:
                    result.add(BeanUtils.obj2String(item.cityName()));
                    break;
                case POST_CODE:
                    result.add(BeanUtils.obj2String(item.postCode()));
                    break;
                case TYPE_CODE:
                    result.add(BeanUtils.obj2String(item.typeCode()));
                    break;
                case PROVINCE_CODE:
                    result.add(BeanUtils.obj2String(item.provinceCode()));
                    break;
                case PROVINCE_NAME:
                    result.add(BeanUtils.obj2String(item.provinceName()));
                    break;
            }
        }
        return result.toArray(new String[0]);
    }
    private void writeSqlScript(List<POI.Info> res, List<POI.OutputFields> outputFields) {
        if (!configHolder.hasStart) return;
        String filename = filename("sql");
        File sqlFile = FileUtil.getNewFile(filename);
        if (sqlFile == null) {
            appendMessage("输出路径有误，请检查后重试！");
            return;
        }
        try (BufferedWriter writer = Files.newBufferedWriter(sqlFile.toPath(), StandardCharsets.UTF_8)) {
            appendMessage("正在写入数据，请等待");
            Date date=new Date();
            SimpleDateFormat sf=new SimpleDateFormat();
            String format=DateFormatUtils.format(date,"yyyy-MM-dd HH:mm:ss");
            String type="residential";
            String scopeId="4501";
            String scopeName="住宅小区";
            for (POI.Info item : res) {
                String[] lnglat = BeanUtils.obj2String(item.location()).split(",");
                if (null!=item.photos()){
                    List<Map<String,String>> maps = JacksonUtils.json2List(item.photos().toString(), Map.class);
                    if (maps.size()>0){
                        Map<String, String> firstPhoto = maps.get(0);
                        String logo = firstPhoto.get("url");
                        writer.write("INSERT INTO service.cube_merchant (MERCHANT_ID,MERCHANT_NAME,OWNER_USER_ID," +
                                "KEYWORDS,LONGITUDE,LATITUDE,ADDRESS,TELEPHONE,NATION,PROVINCE,CITY,DISTRICT,CREATE_TIME,MERCHANT_TYPE," +
                                "AUDIT_USER_ID,AUDIT_STATUS,SERVICE_TIME_START,SERVICE_TIME_END,LOCATION_GEO_HASH,logo) " +
                                "VALUES('"+BeanUtils.obj2String(item.poiId())+"','"+BeanUtils.obj2String(item.name())+"',''," +
                                "'"+BeanUtils.obj2String(item.type())+"',"+lnglat[0]+","+lnglat[1]+",'"
                                +BeanUtils.obj2String(item.address())+"','"+BeanUtils.obj2String(item.tel())+"'," +
                                "'中国','"+BeanUtils.obj2String(item.provinceName())+"','"+BeanUtils.obj2String(item.cityName())+"'" +
                                ",'"+BeanUtils.obj2String(item.adName())+"','"+format+"','"+type+"','sysinit','1','0000','2400','"+
                                GeoHashUtil.encodeLatLon(lnglat[1],lnglat[0])+"','"+logo+"');\r\n");
                        for (Map<String,String> map:maps){
                            String url = map.get("url");
                            writer.write("INSERT INTO service.cube_merchant_file (file_id, merchant_id, file_url, file_type, create_time) " +
                                    "VALUES (REPLACE(UUID(),'-',''), '"+BeanUtils.obj2String(item.poiId())+"', '"+url+"', '0', '"+format+"');\r\n");
                        }
                    }else{
                        writer.write("INSERT INTO service.cube_merchant (MERCHANT_ID,MERCHANT_NAME,OWNER_USER_ID," +
                                "KEYWORDS,LONGITUDE,LATITUDE,ADDRESS,TELEPHONE,NATION,PROVINCE,CITY,DISTRICT,CREATE_TIME,MERCHANT_TYPE," +
                                "AUDIT_USER_ID,AUDIT_STATUS,SERVICE_TIME_START,SERVICE_TIME_END,LOCATION_GEO_HASH) " +
                                "VALUES('"+BeanUtils.obj2String(item.poiId())+"','"+BeanUtils.obj2String(item.name())+"',''," +
                                "'"+BeanUtils.obj2String(item.type())+"',"+lnglat[0]+","+lnglat[1]+",'"
                                +BeanUtils.obj2String(item.address())+"','"+BeanUtils.obj2String(item.tel())+"'," +
                                "'中国','"+BeanUtils.obj2String(item.provinceName())+"','"+BeanUtils.obj2String(item.cityName())+"'" +
                                ",'"+BeanUtils.obj2String(item.adName())+"','"+format+"','"+type+"','sysinit','1','00:00','24:00','"+
                                GeoHashUtil.encodeLatLon(lnglat[1],lnglat[0])+"');\r\n");
                    }
                }else{
                    writer.write("INSERT INTO service.cube_merchant (MERCHANT_ID,MERCHANT_NAME,OWNER_USER_ID," +
                            "KEYWORDS,LONGITUDE,LATITUDE,ADDRESS,TELEPHONE,NATION,PROVINCE,CITY,DISTRICT,CREATE_TIME,MERCHANT_TYPE," +
                            "AUDIT_USER_ID,AUDIT_STATUS,SERVICE_TIME_START,SERVICE_TIME_END,LOCATION_GEO_HASH) " +
                            "VALUES('"+BeanUtils.obj2String(item.poiId())+"','"+BeanUtils.obj2String(item.name())+"',''," +
                            "'"+BeanUtils.obj2String(item.type())+"',"+lnglat[0]+","+lnglat[1]+",'"
                            +BeanUtils.obj2String(item.address())+"','"+BeanUtils.obj2String(item.tel())+"'," +
                            "'中国','"+BeanUtils.obj2String(item.provinceName())+"','"+BeanUtils.obj2String(item.cityName())+"'" +
                            ",'"+BeanUtils.obj2String(item.adName())+"','"+format+"','"+type+"','sysinit','1','0000','2400','"+
                            GeoHashUtil.encodeLatLon(lnglat[1],lnglat[0])+"');\r\n");
                }
                writer.write("INSERT INTO service.cube_merchant_service_scope (id, scope_id, scope_name, ordinal, merchant_id) " +
                        "VALUES (REPLACE(UUID(),'-',''), '"+scopeId+"', '"+scopeName+"', 99, '"+BeanUtils.obj2String(item.poiId())+"');\r\n");

            }
            appendMessage("写入成功，结果存储于" + sqlFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            appendMessage("写入失败");
            appendMessage(e.getMessage());
        }
    }
    private void writeToCsv(List<POI.Info> res, List<POI.OutputFields> outputFields) {
        if (!configHolder.hasStart) return;
        String filename = filename("csv");
        File csvFile = FileUtil.getNewFile(filename);
        if (csvFile == null) {
            appendMessage("输出路径有误，请检查后重试！");
            return;
        }
        try (CSVWriter writer = new CSVWriter(Files.newBufferedWriter(csvFile.toPath(), StandardCharsets.UTF_8))) {
            appendMessage("正在写入数据，请等待");
            writer.writeNext(header(outputFields));
            for (POI.Info info : res) {
                writer.writeNext(content(info, outputFields));
            }
            appendMessage("写入成功，结果存储于" + csvFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            appendMessage("写入失败");
            appendMessage(e.getMessage());
        }
    }

    private void writeToGeoJson(List<POI.Info> res, List<POI.OutputFields> outputFields) {
        if (!configHolder.hasStart) return;
        String filename = filename("json");
        File jsonFile = FileUtil.getNewFile(filename);
        if (jsonFile == null) {
            appendMessage("输出路径有误，请检查后重试！");
            return;
        }
        try (BufferedWriter writer = Files.newBufferedWriter(jsonFile.toPath(), StandardCharsets.UTF_8)) {
            appendMessage("正在写入数据，请等待");
            writer.write("{" +
                    "\"type\":\"" + "FeatureCollection" + "\"" +
                    ", \"features\": [");
            boolean first = true;
            for (POI.Info item : res) {
                Feature feature = getFeatureFromInfo(item, outputFields);
                if (feature != null) {
                    if (!first)
                        writer.write(",");
                    writer.write(feature.toString());
                    first = false;
                }
            }
            writer.write("]" +
                    "}");
            appendMessage("写入成功，结果存储于" + jsonFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            appendMessage("写入失败");
            appendMessage(e.getMessage());
        }
    }

    private String createTypeSpecForShp(List<POI.OutputFields> outputFields) {
        // 对于 shapefile 和 GeoJSON 数据，坐标信息一定会导出，这里仅会影响 properties 中的值
        String[] header = outputFields.stream()
                .map(POI.OutputFields::shapeFieldName)
                .toArray(String[]::new);
        StringBuilder result = new StringBuilder();
        for (String item : header) {
            result.append(item).append(":String").append(",");
        }
        result.deleteCharAt(result.length() - 1);
        return result.toString();
    }

    private void writeToShp(List<POI.Info> res, List<POI.OutputFields> outputFields) {
        if (!configHolder.hasStart) return;
        String filename = filename("shp");
        appendMessage("正在写入数据，请等待");
        try {
            final SimpleFeatureType type =
                    DataUtilities.createType(
                            "Location",
                            "the_geom:Point:srid=4326," + createTypeSpecForShp(outputFields)
                    );
            List<SimpleFeature> features = new ArrayList<>();
            GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
            SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(type);
            for (POI.Info info : res) {
                String[] lnglat = info.location().toString().split(",");
                if (lnglat.length == 2) {
                    double[] wgs84 = CoordinateTransformUtil.transformGCJ02ToWGS84(Double.parseDouble(lnglat[0]), Double.parseDouble(lnglat[1]));
                    Point point = geometryFactory.createPoint(new Coordinate(wgs84[0], wgs84[1]));
                    featureBuilder.add(point);

                    // attrs
                    String[] content = content(info, outputFields);
                    for (String item : content) {
                        featureBuilder.add(item);
                    }
                    SimpleFeature feature = featureBuilder.buildFeature(null);
                    features.add(feature);
                }
            }
            File shpFile = FileUtil.getNewFile(filename);
            if (shpFile == null) {
                appendMessage("文件无法创建，写入失败！");
                return;
            }
            if (SpatialDataTransformUtil.saveFeaturesToShp(features, type, shpFile.getAbsolutePath())) {
                appendMessage("写入成功，结果存储于" + shpFile.getAbsolutePath());
            } else appendMessage("写入失败");
        } catch (SchemaException e) {
            e.printStackTrace();
            appendMessage("写入失败");
        }
    }

    private Feature getFeatureFromInfo(POI.Info item, List<POI.OutputFields> outputFields) {
        String[] lnglat = BeanUtils.obj2String(item.location()).split(",");
        double[] wgs84;
        if (lnglat.length == 2) {
            wgs84 = CoordinateTransformUtil.transformGCJ02ToWGS84(Double.parseDouble(lnglat[0]), Double.parseDouble(lnglat[1]));
        } else {
            return null;
        }
        JsonObject geometry = new JsonObject();
        geometry.addProperty("type", "Point");
        JsonArray coordinates = new JsonArray();
        coordinates.add(wgs84[0]);
        coordinates.add(wgs84[1]);
        geometry.add("coordinates", coordinates);

        Map<String, String> properties = new HashMap<>();
        for (POI.OutputFields field : outputFields) {
            switch (field) {
                case ID:
                    properties.put(field.fieldName(), BeanUtils.obj2String(item.poiId()));
                    break;
                case TEL:
                    properties.put(field.fieldName(), BeanUtils.obj2String(item.tel()));
                    break;
                case NAME:
                    properties.put(field.fieldName(), BeanUtils.obj2String(item.name()));
                    break;
                case TYPE:
                    properties.put(field.fieldName(), BeanUtils.obj2String(item.type()));
                    break;
                case EMAIL:
                    properties.put(field.fieldName(), BeanUtils.obj2String(item.email()));
                    break;
                case AD_CODE:
                    properties.put(field.fieldName(), BeanUtils.obj2String(item.adCode()));
                    break;
                case AD_NAME:
                    properties.put(field.fieldName(), BeanUtils.obj2String(item.adName()));
                    break;
                case ADDRESS:
                    properties.put(field.fieldName(), BeanUtils.obj2String(item.address()));
                    break;
                case WEBSITE:
                    properties.put(field.fieldName(), BeanUtils.obj2String(item.website()));
                    break;
                case BIZ_TYPE:
                    properties.put(field.fieldName(), BeanUtils.obj2String(item.bizType()));
                    break;
                case GCJ02_LNG:
                    properties.put(field.fieldName(), lnglat[0]);
                    break;
                case GCJ02_LAT:
                    properties.put(field.fieldName(), lnglat[1]);
                    break;
                case WGS84_LNG:
                    properties.put(field.fieldName(), BeanUtils.obj2String(wgs84[0]));
                    break;
                case WGS84_LAT:
                    properties.put(field.fieldName(), BeanUtils.obj2String(wgs84[1]));
                    break;
                case CITY_CODE:
                    properties.put(field.fieldName(), BeanUtils.obj2String(item.cityCode()));
                    break;
                case CITY_NAME:
                    properties.put(field.fieldName(), BeanUtils.obj2String(item.cityName()));
                    break;
                case POST_CODE:
                    properties.put(field.fieldName(), BeanUtils.obj2String(item.postCode()));
                    break;
                case TYPE_CODE:
                    properties.put(field.fieldName(), BeanUtils.obj2String(item.typeCode()));
                    break;
                case PROVINCE_CODE:
                    properties.put(field.fieldName(), BeanUtils.obj2String(item.provinceCode()));
                    break;
                case PROVINCE_NAME:
                    properties.put(field.fieldName(), BeanUtils.obj2String(item.provinceName()));
                    break;
            }
        }
        return new Feature(geometry.toString(), properties);
    }

    private boolean parseRect(String text) {
        String pattern = "^(-?\\d{1,3}(\\.\\d+)?),\\s?(-?\\d{1,3}(\\.\\d+)?)#(-?\\d{1,3}(\\.\\d+)?),\\s?(-?\\d{1,3}(\\.\\d+)?)$";
        return Pattern.matches(pattern, text);
    }

    private void clearMessage() {
        Platform.runLater(() -> viewHolder.messageDetail.clear());
    }

    private void appendMessage(String text) {
        Platform.runLater(() -> viewHolder.messageDetail.appendText(text + "\r\n"));
    }
}
