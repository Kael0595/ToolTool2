package com.ll.sbb.User;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.ll.sbb.Article.Article;
import com.ll.sbb.Article.ArticleService;
import com.ll.sbb.Email.TempKey;
import com.ll.sbb.Market.Market;
import com.ll.sbb.Market.MarketService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final TempKey tempKey;

    private final ArticleService articleService;

    private final MarketService marketService;

    private final UserService userService;

    @GetMapping("/user/signup_menual")
    private String signup_manual() {
        return "signup_menual";
    }

    @GetMapping("/user/signup")
    private String signup(UserCreateForm userCreateForm) {
        return "signup_form";
    }

    @PostMapping("/user/signup")
    private String signup(@Valid UserCreateForm userCreateForm, BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            return "signup_form";
        }

        if (!userCreateForm.getPassword1().equals(userCreateForm.getPassword2())) {
            bindingResult.rejectValue("password2", "비밀번호 2개가 일치하지 않습니다.");
            return "signup_form";
        }
        try {
            String mailKey = tempKey.getKey(10, true); // 이메일 인증을 위한 랜덤 키 생성
            userCreateForm.setMailKey(mailKey); // 사용자 폼에 인증번호 설정
            userService.emailConfirm(userCreateForm.getEmail(), userCreateForm.getMailKey());
            userService.create(userCreateForm.getUsername(),
                    userCreateForm.getPassword1(), userCreateForm.getEmail(), userCreateForm.getNickname());
        } catch (DataIntegrityViolationException e) {
            e.printStackTrace();
            bindingResult.reject("signupFailed", "이미 등록된 사용자입니다.");
            return "signup_form";
        } catch (Exception e) {
            e.printStackTrace();
            bindingResult.reject("signupFailed", e.getMessage());
            return "signup_form";
        }
        return "redirect:/";
    }

    @PostMapping("/user/email_confirm")
    public String emailConfirm(@RequestParam("email") String email,
                               @RequestParam("mailKey") String mailKey,
                               Model model) {
        try {
            userService.emailConfirm(email, mailKey);
            model.addAttribute("message", "이메일 인증이 완료되었습니다.");
        } catch (Exception e) {
            model.addAttribute("error", "유효하지 않은 이메일 또는 메일 키입니다.");
        }
        return "email_confirm_result";
    }

    @GetMapping("/user/login")
    public String login() {
        return "login_form";
    }

    @PostMapping("/user/login")
    public String login(HttpSession session, @RequestParam("username") String username, @RequestParam("password") String password) {
        if (userService.authenticateUser(username, password)) {
            session.setAttribute("loggedIn", true);

            SiteUser user = userService.getUser(username);
            session.setAttribute("user", user);

            return "redirect:/";
        } else {
            return "login_form";
        }
    }


    @GetMapping("/login/oauth2/code/kakao")
    public @ResponseBody String kakaoCallback(String code) {

        //POST방식으로 key=value 데이터 요청(카카오쪽으로)
        //Retrofit2
        //OkHttp
        //RestTemplate
        RestTemplate rt = new RestTemplate();

        //HttpHeader 오브젝트 생성
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        //HttpBody 오브젝트 생성
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant-type", "authorization_code");
        params.add("client_key", "52a81a61b5875bfb11fea3e2e2aa2450");
        params.add("redirect_url", "http://localhost:8080/login/oauth2/code/kakao");
        params.add("code", code);

        //HttpHeader와 HttpBody를 하나의 오브젝트에 담기
        HttpEntity<MultiValueMap<String, String>> kakaoTokenRequest =
                new HttpEntity<>(params, headers);

        //Http 요청하기 - POST 방식으로 그리고 response 변수의 응답
        ResponseEntity<String> response = rt.exchange("https://kauth.kakao.com/oauth/token"
                , HttpMethod.POST, kakaoTokenRequest, String.class);
        return "카카오 토큰 요청 완료 : 토큰에 대한 응답 : " + response;
    }


    @GetMapping("/user/logout")
    public String Logout(HttpSession session) {
        session.removeAttribute("loggedIn");
        return "redirect:/";
    }

    @GetMapping("/mypage")
    private String mypage(Model model, @RequestParam(value = "page", defaultValue = "1") int page,
                          @RequestParam(value = "kw", defaultValue = "") String kw) {
        Page<Article> paging = this.articleService.getList(page, kw);
        List<Article> articles = this.articleService.getAll();
        List<Market> markets = this.marketService.getAll();
        int articleCount = articles.size();
        int marketCount = markets.size();
        model.addAttribute("markets", markets);
        model.addAttribute("marketCount", marketCount);
        model.addAttribute("articles", articles);
        model.addAttribute("articleCount", articleCount);
        model.addAttribute("paging", paging);
        model.addAttribute("kw", kw);
        return "mypage";
    }
}
