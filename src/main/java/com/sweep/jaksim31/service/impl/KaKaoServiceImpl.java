package com.sweep.jaksim31.service.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.sweep.jaksim31.auth.CustomLoginIdPasswordAuthToken;
import com.sweep.jaksim31.auth.CustomUserDetailsService;
import com.sweep.jaksim31.auth.TokenProvider;
import com.sweep.jaksim31.dto.login.KaKaoInfoDTO;
import com.sweep.jaksim31.dto.member.MemberRespDTO;
import com.sweep.jaksim31.dto.token.TokenDTO;
import com.sweep.jaksim31.entity.members.MemberRepository;
import com.sweep.jaksim31.entity.members.Members;
import com.sweep.jaksim31.entity.token.RefreshToken;
import com.sweep.jaksim31.entity.token.RefreshTokenRepository;
import com.sweep.jaksim31.service.KakaoService;
import com.sweep.jaksim31.util.CookieUtil;
import com.sweep.jaksim31.util.exceptionhandler.BizException;
import com.sweep.jaksim31.util.exceptionhandler.MemberExceptionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

@Slf4j
@Service
@RequiredArgsConstructor
public class KaKaoServiceImpl implements KakaoService {

    private final AuthenticationManager authenticationManager;
    private final MemberRepository memberRepository;
    private final TokenProvider tokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CustomUserDetailsService customUserDetailsService;
    @Value("${jwt.refresh-token-expire-time}")
    private long rtkLive;

    @Value("${jwt.access-token-expire-time}")
    private long accExpTime;


    @Override
    @Transactional
    public ResponseEntity<MemberRespDTO> kakaosignup(KaKaoInfoDTO memberRequestDto) {
        if (memberRepository.existsByLoginId(memberRequestDto.getLoginId())) {
            throw new BizException(MemberExceptionType.DUPLICATE_USER);
        }

        Members members = memberRequestDto.toMember();
        if(members.getPassword() == null)
            members.setIsSocial(true);
        log.debug("member = {}", members);

        return ResponseEntity.ok(MemberRespDTO.of(memberRepository.save(members)));
    }

    @Override
    @Transactional
    public ResponseEntity<TokenDTO> kakaologin(KaKaoInfoDTO loginReqDTO, HttpServletResponse response) {
        CustomLoginIdPasswordAuthToken customLoginIdPasswordAuthToken =
                new CustomLoginIdPasswordAuthToken(loginReqDTO.getLoginId(),"");
        System.out.println(customLoginIdPasswordAuthToken);
        System.out.println("Step 1 Complete.");

        Authentication authenticate = authenticationManager.authenticate(customLoginIdPasswordAuthToken);
        System.out.println("Step 2 Complete");
        String loginId = authenticate.getName();
        Members members = customUserDetailsService.getMember(loginId);

        //System.out.println("Step 2 Complete");
        String accessToken = tokenProvider.createAccessToken(loginId, members.getAuthorities());
        String refreshToken = tokenProvider.createRefreshToken(loginId, members.getAuthorities());


        int cookieMaxAge = (int) rtkLive / 60;

//        CookieUtil.addCookie(response, "access_token", accessToken, cookieMaxAge);
        CookieUtil.addCookie(response, "refresh_token", refreshToken, cookieMaxAge);

        // 로그인 여부 및 토큰 만료 시간 Cookie 설정
        String isLogin = "true";
        Date newExpTime = new Date(System.currentTimeMillis() + accExpTime);
        SimpleDateFormat sdf;
        sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        String expTime = sdf.format(newExpTime);
        CookieUtil.addPublicCookie(response, "isLogin", isLogin, cookieMaxAge);
        CookieUtil.addPublicCookie(response, "expTime", expTime, cookieMaxAge);
//        System.out.println("redis " + redisService.getValues(loginId));

        //db에 token 저장
        refreshTokenRepository.save(
                RefreshToken.builder()
                        .loginId(loginId)
                        .value(refreshToken)
                        .build()
        );

        return ResponseEntity.ok(tokenProvider.createTokenDTO(accessToken,refreshToken, expTime,loginId));

    }

    // 토큰 받아오기
    public String getAccessToken(String code) {
        String accessToken = "";
        String refreshToken = "";
        String reqURL = "https://kauth.kakao.com/oauth/token";

        try {
            URL url = new URL(reqURL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(conn.getOutputStream()));
            StringBuilder sb = new StringBuilder();
            sb.append("grant_type=authorization_code");
            sb.append("&client_id=9fadcf52b1b1a29c3e8b9d5047151491"); //REST API 키
            sb.append("&redirect_uri=http://localhost:8080/v0/auth/kakaologin");
            sb.append("&code="+code);

            bw.write(sb.toString());
            bw.flush();

            int responseCode = conn.getResponseCode();
            System.out.println("response code = " + responseCode);

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));

            String line = "";
            String result = "";
            while((line = br.readLine())!=null) {
                result += line;
            }
            System.out.println("response body="+result);

            JsonParser parser = new JsonParser();
            JsonElement element = parser.parse(result);

            accessToken = element.getAsJsonObject().get("access_token").getAsString();
            refreshToken = element.getAsJsonObject().get("refresh_token").getAsString();

            br.close();
            bw.close();
        }catch (Exception e) {
            e.printStackTrace();
        }
        return accessToken;
    }

    public KaKaoInfoDTO getUserInfo(String accessToken) {
        KaKaoInfoDTO userInfo = new KaKaoInfoDTO();
        String reqUrl = "https://kapi.kakao.com/v2/user/me"; //Request - AccessToken 사용
        try {
            URL url = new URL(reqUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            int responseCode = conn.getResponseCode();
            System.out.println("responseCode =" + responseCode);

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            System.out.println(br);
            String line = "";
            String result = "";

            while((line = br.readLine()) != null) {
                result += line;
            }
            System.out.println("response body ="+result);

//            ObjectMapper mapper = new ObjectMapper();
//            JSONParser jsonParser = new JSONParser();
//            String test = mapper.writeValueAsString(result);
//
//            JSONObject object = (JSONObject) jsonParser.parse(test);
//            System.out.println(object.toString());

            // Kakao UserInfo 를 담은 Result를 DTO 형식으로 변환 후 Return
            JSONParser jsonParser = new JSONParser();
            Object obj = jsonParser.parse(result);
            JSONObject jsonObj = (JSONObject) obj;
            JSONObject properties = (JSONObject) jsonObj.get("properties");
            System.out.println(jsonObj.get("id"));
            System.out.println(properties.get("nickname"));
            System.out.println(properties.get("profile_image"));

            KaKaoInfoDTO kakaoInfoDTO = new KaKaoInfoDTO().builder()
                    .loginId(jsonObj.get("id").toString())
                    .userName(properties.get("nickname").toString())
                    .profileImage(properties.get("profile_image").toString())
                    .build();

            return kakaoInfoDTO;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }



    public void kakaoLogout(String accessToken) {
        String reqURL = "http://kapi.kakao.com/v1/user/logout"; //로그아웃 액세스 토큰
        try {
            URL url = new URL(reqURL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
            int responseCode = conn.getResponseCode();
            System.out.println("responseCode = " + responseCode);

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));

            String result = "";
            String line = "";

            while((line = br.readLine()) != null) {
                result+=line;
            }
            System.out.println(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
