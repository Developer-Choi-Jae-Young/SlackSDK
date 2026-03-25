package co.acta.slacksdk.aop;

import co.acta.slacksdk.anno.SlackReplyDelete;
import co.acta.slacksdk.anno.SlackReplyUpdate;
import co.acta.slacksdk.interfaces.SlackMessageable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Aspect
@Slf4j
@Component
@RequiredArgsConstructor
public class SlackAspect {
    private final RestTemplate restTemplate;

    @Value("${slack.board.add.url}")
    private String slackBoardAddUrl;
    @Value("${slack.board.reply.delete.url}")
    private String slackBoardReplyDeleteUrl;
    @Value("${slack.board.reply.update.url}")
    private String slackBoardReplyUpdateUrl;


    @AfterReturning(pointcut = "@annotation(co.acta.slacksdk.anno.SlackMessage) || " +
            "@annotation(co.acta.slacksdk.anno.SlackReplyUpdate) || " +
            "@annotation(co.acta.slacksdk.anno.SlackReplyDelete)", returning = "result")
    public void afterReturning(JoinPoint joinPoint, Object result) {
        if (result == null) return;

        try {
            Object[] args = joinPoint.getArgs();
            SlackMessageable vo = null;
            List<MultipartFile> files = new ArrayList<>();

            for (Object arg : args) {
                if (arg instanceof SlackMessageable) vo = (SlackMessageable) arg;
                else if (arg instanceof MultipartFile) files.add((MultipartFile) arg);
                else if (arg instanceof List) {
                    List<?> list = (List<?>) arg;
                    if (!list.isEmpty() && list.get(0) instanceof MultipartFile) {
                        for (Object item : list) files.add((MultipartFile) item);
                    }
                }
            }

            if (vo == null) return;

            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            sendByAction(signature.getMethod(), vo, files);

        } catch (Exception e) {
            log.error("Slack 알림 프로세스 실패", e);
        }
    }

    private void sendByAction(Method method, SlackMessageable vo, List<MultipartFile> files) {
        Map<String, Object> dtoMap = new HashMap<>();
        String targetUrl = slackBoardAddUrl;

        if (method.isAnnotationPresent(SlackReplyDelete.class)) {
            targetUrl = slackBoardReplyDeleteUrl;
            dtoMap.put("replyId", vo.getSDKReplyId());
            dtoMap.put("status", "DELETED");
        } else if (method.isAnnotationPresent(SlackReplyUpdate.class)) {
            targetUrl = slackBoardReplyUpdateUrl;
            dtoMap.put("replyId", vo.getSDKReplyId());
            dtoMap.put("content", vo.getSDKContent());
            dtoMap.put("status", "UPDATED");
        } else {
            if (vo.getSDKTitle() != null && !vo.getSDKTitle().isEmpty()) dtoMap.put("title", vo.getSDKTitle());
            if (vo.getSDKParentBoardId() != null && !vo.getSDKParentBoardId().isEmpty()) dtoMap.put("parentBoardId", vo.getSDKParentBoardId());
            dtoMap.put("boardId", vo.getSDKBoardId());
            dtoMap.put("content", vo.getSDKContent());
            dtoMap.put("writer", vo.getSDKWriter());
            dtoMap.put("regDate", vo.getSDKRegDate());
            dtoMap.put("link", vo.getSDKBoardId());
        }

        executePost(targetUrl, dtoMap, files);
    }

    private void executePost(String url, Map<String, Object> dtoMap, List<MultipartFile> files) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attr != null) {
            String referer = attr.getRequest().getHeader("Referer");
            if (referer != null) headers.set("Referer", referer);
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("dto", dtoMap);
        for (MultipartFile mFile : files) {
            if (mFile != null && !mFile.isEmpty()) body.add("files", mFile.getResource());
        }

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(url, entity, Object.class);
    }
}
