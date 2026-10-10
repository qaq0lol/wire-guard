# -*- coding: utf-8 -*-
import os
import math

OUT_DIR = r"c:\Users\Administrator\Desktop\代理软件\app\src\main\res\drawable"

def star_path(cx, cy, r_outer, r_inner=None, points=5, start_angle=-math.pi/2):
    if r_inner is None:
        r_inner = r_outer * 0.382
    pts = []
    angle_step = math.pi / points
    for i in range(2 * points):
        r = r_outer if i % 2 == 0 else r_inner
        ang = start_angle + i * angle_step
        x = cx + r * math.cos(ang)
        y = cy + r * math.sin(ang)
        pts.append(f"{x:.1f},{y:.1f}")
    return "M" + "L".join(pts) + "Z"

def circle_path(cx, cy, r):
    return f"M{cx},{cy-r}a{r},{r} 0 1,0 0.1,0z"

flags = {}

# 1. ae (United Arab Emirates)
flags["flag_ae.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Green Top -->
    <path android:fillColor="#00732F" android:pathData="M160,0h480v160h-480z" />
    <!-- White Middle -->
    <path android:fillColor="#FFFFFF" android:pathData="M160,160h480v160h-480z" />
    <!-- Black Bottom -->
    <path android:fillColor="#000000" android:pathData="M160,320h480v160h-480z" />
    <!-- Red Hoist Band -->
    <path android:fillColor="#FF0000" android:pathData="M0,0h160v480h-160z" />
</vector>
"""

# 2. al (Albania)
flags["flag_al.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Red Field -->
    <path android:fillColor="#E41E20" android:pathData="M0,0h640v480h-640z" />
    <!-- Double-Headed Eagle Silhouette -->
    <path android:fillColor="#000000" android:pathData="M320,120 L335,140 L360,130 L350,155 L385,150 L410,180 L445,190 L425,220 L460,240 L430,265 L445,290 L410,290 L390,320 L370,305 L355,350 L340,335 L335,390 L320,380 L305,390 L300,335 L285,350 L270,305 L250,320 L230,290 L195,290 L210,265 L180,240 L215,220 L195,190 L230,180 L255,150 L290,155 L280,130 L305,140 Z" />
    <!-- Eagle Wings Detail -->
    <path android:fillColor="#000000" android:pathData="M320,170 L340,195 L370,185 L355,215 L385,225 L350,245 L320,230 L290,245 L255,225 L285,215 L270,185 L300,195 Z" />
</vector>
"""

# 3. aq (Antarctica)
flags["flag_aq.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Ocean Blue Field -->
    <path android:fillColor="#003399" android:pathData="M0,0h640v480h-640z" />
    <!-- White Antarctica Silhouette -->
    <path android:fillColor="#FFFFFF" android:pathData="M230,135 C265,110 330,120 375,140 C420,160 470,190 475,240 C480,290 445,340 395,355 C345,370 280,360 235,335 C190,310 170,270 175,225 C180,180 195,160 230,135 Z M205,190 L185,130 L210,145 Z" />
</vector>
"""

# 4. ar (Argentina)
ar_sun = f"""    <!-- Sun of May Disc -->
    <path android:fillColor="#F6B40E" android:pathData="{circle_path(320, 240, 34)}" />
    <!-- Sun of May Rays -->
    <path android:strokeColor="#F6B40E" android:strokeWidth="5" android:pathData="M320,185v30 M320,265v30 M265,240h30 M345,240h30 M281,201l21,21 M338,258l21,21 M359,201l-21,21 M260,300l21,-21 M300,188l10,24 M340,188l-10,24 M300,292l10,-24 M340,292l-10,-24 M268,220l24,10 M268,260l24,-10 M372,220l-24,10 M372,260l-24,-10" />"""

flags["flag_ar.xml"] = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Light Blue Top -->
    <path android:fillColor="#74ACDF" android:pathData="M0,0h640v160h-640z" />
    <!-- White Middle -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,160h640v160h-640z" />
    <!-- Light Blue Bottom -->
    <path android:fillColor="#74ACDF" android:pathData="M0,320h640v160h-640z" />
{ar_sun}
</vector>
"""

# 5. at (Austria)
flags["flag_at.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Red Top -->
    <path android:fillColor="#ED2939" android:pathData="M0,0h640v160h-640z" />
    <!-- White Middle -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,160h640v160h-640z" />
    <!-- Red Bottom -->
    <path android:fillColor="#ED2939" android:pathData="M0,320h640v160h-640z" />
</vector>
"""

# 6. ba (Bosnia and Herzegovina)
ba_stars = "\n".join([f'    <path android:fillColor="#FFFFFF" android:pathData="{star_path(x, y, 16)}" />' 
                      for (x, y) in [(200, 35), (240, 90), (280, 145), (320, 200), (360, 255), (400, 310), (440, 365), (480, 420)]])

flags["flag_ba.xml"] = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Blue Field -->
    <path android:fillColor="#002395" android:pathData="M0,0h640v480h-640z" />
    <!-- Yellow Triangle -->
    <path android:fillColor="#FECB00" android:pathData="M165,0 L515,0 L515,480 Z" />
{ba_stars}
</vector>
"""

# 7. be (Belgium)
flags["flag_be.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Black Left -->
    <path android:fillColor="#000000" android:pathData="M0,0h213v480h-213z" />
    <!-- Yellow Center -->
    <path android:fillColor="#FDDA24" android:pathData="M213,0h214v480h-214z" />
    <!-- Red Right -->
    <path android:fillColor="#EF3340" android:pathData="M427,0h213v480h-213z" />
</vector>
"""

# 8. bg (Bulgaria)
flags["flag_bg.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- White Top -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,0h640v160h-640z" />
    <!-- Green Middle -->
    <path android:fillColor="#00966E" android:pathData="M0,160h640v160h-640z" />
    <!-- Red Bottom -->
    <path android:fillColor="#D62612" android:pathData="M0,320h640v160h-640z" />
</vector>
"""

# 9. br (Brazil)
flags["flag_br.xml"] = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Green Field -->
    <path android:fillColor="#009C3B" android:pathData="M0,0h640v480h-640z" />
    <!-- Yellow Rhombus -->
    <path android:fillColor="#FFDF00" android:pathData="M320,40 L580,240 L320,440 L60,240 Z" />
    <!-- Blue Circle -->
    <path android:fillColor="#002776" android:pathData="{circle_path(320, 240, 105)}" />
    <!-- White Arc Band -->
    <path android:fillColor="#FFFFFF" android:pathData="M217,250 C250,210 370,205 423,248 C375,220 255,225 217,250 Z" />
    <!-- Southern Cross Stars -->
    <path android:fillColor="#FFFFFF" android:pathData="{star_path(320, 275, 6)} {star_path(310, 295, 5)} {star_path(330, 290, 5)} {star_path(320, 310, 6)} {star_path(335, 305, 4)}" />
</vector>
"""

# 10. ca (Canada)
flags["flag_ca.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Red Left Stripe -->
    <path android:fillColor="#FF0000" android:pathData="M0,0h160v480h-160z" />
    <!-- White Middle Square -->
    <path android:fillColor="#FFFFFF" android:pathData="M160,0h320v480h-320z" />
    <!-- Red Right Stripe -->
    <path android:fillColor="#FF0000" android:pathData="M480,0h160v480h-160z" />
    <!-- Stylized 11-Point Maple Leaf -->
    <path android:fillColor="#FF0000" android:pathData="M320,85 L334,145 L365,135 L350,175 L392,175 L402,205 L372,220 L410,240 L390,270 L336,252 L342,278 L326,284 L326,395 L314,395 L314,284 L298,278 L304,252 L250,270 L230,240 L268,220 L238,205 L248,175 L290,175 L275,135 L306,145 Z" />
</vector>
"""

# 11. ch (Switzerland)
flags["flag_ch.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Red Field -->
    <path android:fillColor="#D52B1E" android:pathData="M0,0h640v480h-640z" />
    <!-- White Swiss Cross Vertical -->
    <path android:fillColor="#FFFFFF" android:pathData="M275,100h90v280h-90z" />
    <!-- White Swiss Cross Horizontal -->
    <path android:fillColor="#FFFFFF" android:pathData="M180,195h280v90h-280z" />
</vector>
"""

# 12. cl (Chile)
flags["flag_cl.xml"] = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Top White Fly -->
    <path android:fillColor="#FFFFFF" android:pathData="M240,0h400v240h-400z" />
    <!-- Bottom Red Stripe -->
    <path android:fillColor="#D52B1E" android:pathData="M0,240h640v240h-640z" />
    <!-- Blue Canton -->
    <path android:fillColor="#0039A6" android:pathData="M0,0h240v240h-240z" />
    <!-- White Star in Canton -->
    <path android:fillColor="#FFFFFF" android:pathData="{star_path(120, 120, 50)}" />
</vector>
"""

# 13. co (Colombia)
flags["flag_co.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Yellow Top (1/2) -->
    <path android:fillColor="#FCD116" android:pathData="M0,0h640v240h-640z" />
    <!-- Blue Middle (1/4) -->
    <path android:fillColor="#003893" android:pathData="M0,240h640v120h-640z" />
    <!-- Red Bottom (1/4) -->
    <path android:fillColor="#CE1126" android:pathData="M0,360h640v120h-640z" />
</vector>
"""

# 14. cy (Cyprus)
flags["flag_cy.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- White Field -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,0h640v480h-640z" />
    <!-- Copper Map Silhouette -->
    <path android:fillColor="#D57800" android:pathData="M220,230 L270,180 L350,190 L420,130 L450,115 L435,145 L380,210 L340,240 L280,265 L220,250 Z" />
    <!-- Green Olive Branches -->
    <path android:strokeColor="#4E6E2E" android:strokeWidth="12" android:pathData="M210,320 C270,360 370,360 430,320 M260,335l-15,-10 M310,350l-10,-15 M380,345l10,-15" />
</vector>
"""

# 15. cz (Czech Republic)
flags["flag_cz.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- White Top -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,0h640v240h-640z" />
    <!-- Red Bottom -->
    <path android:fillColor="#D7141A" android:pathData="M0,240h640v240h-640z" />
    <!-- Blue Triangle -->
    <path android:fillColor="#11457E" android:pathData="M0,0 L320,240 L0,480 Z" />
</vector>
"""

# 16. dk (Denmark)
flags["flag_dk.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Red Field -->
    <path android:fillColor="#C8102E" android:pathData="M0,0h640v480h-640z" />
    <!-- White Scandinavian Cross Vertical -->
    <path android:fillColor="#FFFFFF" android:pathData="M185,0h70v480h-70z" />
    <!-- White Scandinavian Cross Horizontal -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,205h640v70h-640z" />
</vector>
"""

# 17. ec (Ecuador)
flags["flag_ec.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Yellow Top (1/2) -->
    <path android:fillColor="#FFDD00" android:pathData="M0,0h640v240h-640z" />
    <!-- Blue Middle (1/4) -->
    <path android:fillColor="#034EA2" android:pathData="M0,240h640v120h-640z" />
    <!-- Red Bottom (1/4) -->
    <path android:fillColor="#ED1C24" android:pathData="M0,360h640v120h-640z" />
    <!-- Center Shield & Condor -->
    <path android:fillColor="#A56E24" android:pathData="M320,180 L350,165 L360,180 L340,195 L355,230 L320,285 L285,230 L300,195 L280,180 L290,165 Z" />
    <path android:fillColor="#FFFFFF" android:pathData="M305,210h30v40h-30z" />
    <path android:fillColor="#034EA2" android:pathData="M305,230h30v20h-30z" />
</vector>
"""

# 18. ee (Estonia)
flags["flag_ee.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Blue Top -->
    <path android:fillColor="#0072CE" android:pathData="M0,0h640v160h-640z" />
    <!-- Black Middle -->
    <path android:fillColor="#000000" android:pathData="M0,160h640v160h-640z" />
    <!-- White Bottom -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,320h640v160h-640z" />
</vector>
"""

# 19. es (Spain)
flags["flag_es.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Red Top -->
    <path android:fillColor="#AA151B" android:pathData="M0,0h640v120h-640z" />
    <!-- Yellow Middle (Double Width) -->
    <path android:fillColor="#F1BF00" android:pathData="M0,120h640v240h-640z" />
    <!-- Red Bottom -->
    <path android:fillColor="#AA151B" android:pathData="M0,360h640v120h-640z" />
    <!-- Spanish Coat of Arms Simplified -->
    <!-- Crown -->
    <path android:fillColor="#AA151B" android:pathData="M185,170 L215,170 L220,190 L180,190 Z" />
    <!-- Shield -->
    <path android:fillColor="#AA151B" android:pathData="M180,195h40v35c0,15 -20,25 -20,25c0,0 -20,-10 -20,-25z" />
    <path android:fillColor="#FFFFFF" android:pathData="M185,200h15v15h-15z M200,215h15v15h-15z" />
    <!-- Pillars of Hercules -->
    <path android:fillColor="#C0C0C0" android:pathData="M165,185h8v65h-8z M227,185h8v65h-8z" />
</vector>
"""

# 20. fi (Finland)
flags["flag_fi.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- White Field -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,0h640v480h-640z" />
    <!-- Blue Nordic Cross Vertical -->
    <path android:fillColor="#003580" android:pathData="M180,0h90v480h-90z" />
    <!-- Blue Nordic Cross Horizontal -->
    <path android:fillColor="#003580" android:pathData="M0,195h640v90h-640z" />
</vector>
"""

# 21. fr (France)
flags["flag_fr.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Blue Left -->
    <path android:fillColor="#002654" android:pathData="M0,0h213v480h-213z" />
    <!-- White Center -->
    <path android:fillColor="#FFFFFF" android:pathData="M213,0h214v480h-214z" />
    <!-- Red Right -->
    <path android:fillColor="#ED2939" android:pathData="M427,0h213v480h-213z" />
</vector>
"""

# 22. ge (Georgia)
flags["flag_ge.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- White Field -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,0h640v480h-640z" />
    <!-- St George Cross Vertical -->
    <path android:fillColor="#FF0000" android:pathData="M270,0h100v480h-100z" />
    <!-- St George Cross Horizontal -->
    <path android:fillColor="#FF0000" android:pathData="M0,190h640v100h-640z" />
    <!-- 4 Small Red Crosses -->
    <!-- Top-Left -->
    <path android:fillColor="#FF0000" android:pathData="M125,65h20v60h-20z M105,85h60v20h-60z" />
    <!-- Top-Right -->
    <path android:fillColor="#FF0000" android:pathData="M495,65h20v60h-20z M475,85h60v20h-60z" />
    <!-- Bottom-Left -->
    <path android:fillColor="#FF0000" android:pathData="M125,355h20v60h-20z M105,375h60v20h-60z" />
    <!-- Bottom-Right -->
    <path android:fillColor="#FF0000" android:pathData="M495,355h20v60h-20z M475,375h60v20h-60z" />
</vector>
"""

# 23. gr (Greece)
flags["flag_gr.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Blue Base -->
    <path android:fillColor="#0D5EAF" android:pathData="M0,0h640v480h-640z" />
    <!-- White Stripes (4 stripes) -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,53h640v53h-640z M0,160h640v53h-640z M0,267h640v53h-640z M0,373h640v53h-640z" />
    <!-- Blue Canton -->
    <path android:fillColor="#0D5EAF" android:pathData="M0,0h267v267h-267z" />
    <!-- White Cross in Canton -->
    <path android:fillColor="#FFFFFF" android:pathData="M107,0h53v267h-53z M0,107h267v53h-267z" />
</vector>
"""

# 24. gt (Guatemala)
flags["flag_gt.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Sky Blue Left -->
    <path android:fillColor="#4997D0" android:pathData="M0,0h213v480h-213z" />
    <!-- White Center -->
    <path android:fillColor="#FFFFFF" android:pathData="M213,0h214v480h-214z" />
    <!-- Sky Blue Right -->
    <path android:fillColor="#4997D0" android:pathData="M427,0h213v480h-213z" />
    <!-- Laurel Wreath & Quetzal -->
    <path android:strokeColor="#2E7D32" android:strokeWidth="10" android:pathData="M265,240 C265,285 375,285 375,240" />
    <path android:strokeColor="#795548" android:strokeWidth="6" android:pathData="M270,210 L370,270 M370,210 L270,270" />
    <path android:fillColor="#00E676" android:pathData="M320,200 C335,210 330,245 320,250 C310,245 305,210 320,200 Z" />
</vector>
"""

# 25. hr (Croatia)
hr_squares = []
for row in range(5):
    for col in range(5):
        if (row + col) % 2 == 0:
            color = "#FF0000"
        else:
            color = "#FFFFFF"
        x = 270 + col * 20
        y = 200 + row * 20
        hr_squares.append(f'    <path android:fillColor="{color}" android:pathData="M{x},{y}h20v20h-20z" />')

flags["flag_hr.xml"] = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Red Top -->
    <path android:fillColor="#FF0000" android:pathData="M0,0h640v160h-640z" />
    <!-- White Middle -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,160h640v160h-640z" />
    <!-- Blue Bottom -->
    <path android:fillColor="#171796" android:pathData="M0,320h640v160h-640z" />
    <!-- Crown of 5 Shields -->
    <path android:fillColor="#0038A8" android:pathData="M270,180 L290,165 L310,180 L330,165 L350,180 L370,165 L370,200 L270,200 Z" />
    <!-- Shield Border & Checkerboard -->
    <path android:fillColor="#FFFFFF" android:pathData="M266,196h108v108c0,20 -54,35 -54,35c0,0 -54,-15 -54,-35z" />
{chr(10).join(hr_squares)}
</vector>
"""

# 26. hu (Hungary)
flags["flag_hu.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Red Top -->
    <path android:fillColor="#CE2939" android:pathData="M0,0h640v160h-640z" />
    <!-- White Middle -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,160h640v160h-640z" />
    <!-- Green Bottom -->
    <path android:fillColor="#477050" android:pathData="M0,320h640v160h-640z" />
</vector>
"""

# 27. id (Indonesia)
flags["flag_id.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Red Top -->
    <path android:fillColor="#FF0000" android:pathData="M0,0h640v240h-640z" />
    <!-- White Bottom -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,240h640v240h-640z" />
</vector>
"""

# 28. ie (Ireland)
flags["flag_ie.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Green Left -->
    <path android:fillColor="#169B62" android:pathData="M0,0h213v480h-213z" />
    <!-- White Center -->
    <path android:fillColor="#FFFFFF" android:pathData="M213,0h214v480h-214z" />
    <!-- Orange Right -->
    <path android:fillColor="#FF883E" android:pathData="M427,0h213v480h-213z" />
</vector>
"""

# 29. il (Israel)
flags["flag_il.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- White Field -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,0h640v480h-640z" />
    <!-- Blue Stripes -->
    <path android:fillColor="#0038B8" android:pathData="M0,60h640v50h-640z M0,370h640v50h-640z" />
    <!-- Star of David (Hollow) -->
    <path android:strokeColor="#0038B8" android:strokeWidth="14" android:fillColor="#00000000" android:pathData="M320,165 L395,295 L245,295 Z" />
    <path android:strokeColor="#0038B8" android:strokeWidth="14" android:fillColor="#00000000" android:pathData="M320,315 L395,185 L245,185 Z" />
</vector>
"""

# 30. in (India)
in_spokes = []
for k in range(12):
    ang = k * math.pi / 12
    dx = 40 * math.cos(ang)
    dy = 40 * math.sin(ang)
    in_spokes.append(f'    <path android:strokeColor="#000080" android:strokeWidth="3.5" android:pathData="M{320-dx:.1f},{240-dy:.1f} L{320+dx:.1f},{240+dy:.1f}" />')

flags["flag_in.xml"] = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Saffron Orange Top -->
    <path android:fillColor="#FF9933" android:pathData="M0,0h640v160h-640z" />
    <!-- White Middle -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,160h640v160h-640z" />
    <!-- India Green Bottom -->
    <path android:fillColor="#138808" android:pathData="M0,320h640v160h-640z" />
    <!-- Ashoka Chakra Outer Ring -->
    <path android:strokeColor="#000080" android:strokeWidth="7" android:fillColor="#00000000" android:pathData="{circle_path(320, 240, 44)}" />
    <!-- Center Dot -->
    <path android:fillColor="#000080" android:pathData="{circle_path(320, 240, 9)}" />
{chr(10).join(in_spokes)}
</vector>
"""

# 31. is (Iceland)
flags["flag_is.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Blue Field -->
    <path android:fillColor="#02529C" android:pathData="M0,0h640v480h-640z" />
    <!-- White Cross Vertical -->
    <path android:fillColor="#FFFFFF" android:pathData="M165,0h96v480h-96z" />
    <!-- White Cross Horizontal -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,192h640v96h-640z" />
    <!-- Red Inner Cross Vertical -->
    <path android:fillColor="#DC1E35" android:pathData="M189,0h48v480h-48z" />
    <!-- Red Inner Cross Horizontal -->
    <path android:fillColor="#DC1E35" android:pathData="M0,216h640v48h-640z" />
</vector>
"""

# 32. it (Italy)
flags["flag_it.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Green Left -->
    <path android:fillColor="#009246" android:pathData="M0,0h213v480h-213z" />
    <!-- White Center -->
    <path android:fillColor="#FFFFFF" android:pathData="M213,0h214v480h-214z" />
    <!-- Red Right -->
    <path android:fillColor="#CE2B37" android:pathData="M427,0h213v480h-213z" />
</vector>
"""

# 33. ke (Kenya)
flags["flag_ke.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Black Top -->
    <path android:fillColor="#000000" android:pathData="M0,0h640v140h-640z" />
    <!-- White Stripe 1 -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,140h640v20h-640z" />
    <!-- Red Middle -->
    <path android:fillColor="#BB162B" android:pathData="M0,160h640v160h-640z" />
    <!-- White Stripe 2 -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,320h640v20h-640z" />
    <!-- Green Bottom -->
    <path android:fillColor="#006600" android:pathData="M0,340h640v140h-640z" />
    <!-- Crossed White Spears -->
    <path android:strokeColor="#FFFFFF" android:strokeWidth="10" android:pathData="M250,140 L390,340 M390,140 L250,340" />
    <!-- Maasai Shield -->
    <path android:fillColor="#BB162B" android:pathData="M320,150 C365,190 365,290 320,330 C275,290 275,190 320,150 Z" />
    <!-- Shield Markings -->
    <path android:fillColor="#000000" android:pathData="M320,150 C300,190 300,290 320,330 C290,290 290,190 320,150 Z" />
    <path android:fillColor="#FFFFFF" android:pathData="M320,220h12v40h-12z M320,230a10,10 0 1,0 0.1,0z" />
</vector>
"""

# 34. lt (Lithuania)
flags["flag_lt.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Yellow Top -->
    <path android:fillColor="#FDB913" android:pathData="M0,0h640v160h-640z" />
    <!-- Green Middle -->
    <path android:fillColor="#006A44" android:pathData="M0,160h640v160h-640z" />
    <!-- Red Bottom -->
    <path android:fillColor="#C1272D" android:pathData="M0,320h640v160h-640z" />
</vector>
"""

# 35. lu (Luxembourg)
flags["flag_lu.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Red Top -->
    <path android:fillColor="#EA141D" android:pathData="M0,0h640v160h-640z" />
    <!-- White Middle -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,160h640v160h-640z" />
    <!-- Light Blue Bottom -->
    <path android:fillColor="#00A1DE" android:pathData="M0,320h640v160h-640z" />
</vector>
"""

# 36. lv (Latvia)
flags["flag_lv.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Carmine Red Top -->
    <path android:fillColor="#9E3039" android:pathData="M0,0h640v192h-640z" />
    <!-- Narrow White Middle -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,192h640v96h-640z" />
    <!-- Carmine Red Bottom -->
    <path android:fillColor="#9E3039" android:pathData="M0,288h640v192h-640z" />
</vector>
"""

# 37. md (Moldova)
flags["flag_md.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Blue Left -->
    <path android:fillColor="#003DA5" android:pathData="M0,0h213v480h-213z" />
    <!-- Yellow Center -->
    <path android:fillColor="#FFD100" android:pathData="M213,0h214v480h-214z" />
    <!-- Red Right -->
    <path android:fillColor="#C8102E" android:pathData="M427,0h213v480h-213z" />
    <!-- Moldovan Eagle Emblem -->
    <path android:fillColor="#8D6E63" android:pathData="M320,180 L350,210 L370,195 L360,240 L345,280 L320,295 L295,280 L280,240 L270,195 L290,210 Z" />
    <path android:fillColor="#C8102E" android:pathData="M305,225h30v35c0,10 -15,18 -15,18c0,0 -15,-8 -15,-18z" />
    <path android:fillColor="#FFD100" android:pathData="M320,235a6,6 0 1,0 0.1,0z" />
</vector>
"""

# 38. mk (North Macedonia)
mk_rays = """    <!-- 4 Diagonal Rays -->
    <path android:fillColor="#FFE600" android:pathData="M0,0 L100,0 L320,240 Z M640,0 L540,0 L320,240 Z M640,480 L540,480 L320,240 Z M0,480 L100,480 L320,240 Z" />
    <!-- 4 Orthogonal Rays -->
    <path android:fillColor="#FFE600" android:pathData="M280,0 L360,0 L320,240 Z M280,480 L360,480 L320,240 Z M0,200 L0,280 L320,240 Z M640,200 L640,280 L320,240 Z" />"""

flags["flag_mk.xml"] = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Red Field -->
    <path android:fillColor="#D20000" android:pathData="M0,0h640v480h-640z" />
{mk_rays}
    <!-- Sun Disc with Red Separation Ring -->
    <path android:fillColor="#FFE600" android:pathData="{circle_path(320, 240, 65)}" />
    <path android:strokeColor="#D20000" android:strokeWidth="10" android:fillColor="#FFE600" android:pathData="{circle_path(320, 240, 50)}" />
</vector>
"""

# 39. mx (Mexico)
flags["flag_mx.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Green Left -->
    <path android:fillColor="#006847" android:pathData="M0,0h213v480h-213z" />
    <!-- White Center -->
    <path android:fillColor="#FFFFFF" android:pathData="M213,0h214v480h-214z" />
    <!-- Red Right -->
    <path android:fillColor="#CE1126" android:pathData="M427,0h213v480h-213z" />
    <!-- Mexican Coat of Arms (Eagle on Cactus) -->
    <path android:fillColor="#2E7D32" android:pathData="M290,265 C320,250 320,280 350,265 L340,290 C320,295 310,290 290,290 Z" />
    <path android:fillColor="#5D4037" android:pathData="M315,190 C335,200 340,230 330,260 L310,260 C305,235 305,210 315,190 Z" />
    <path android:strokeColor="#1B5E20" android:strokeWidth="8" android:pathData="M270,270 C280,310 360,310 370,270" />
</vector>
"""

# 40. my (Malaysia)
my_stripes = "\n".join([f'    <path android:fillColor="#FFFFFF" android:pathData="M0,{int(k*34.28)}h640v{int(34.28)}h-640z" />' for k in [1, 3, 5, 7, 9, 11, 13]])

flags["flag_my.xml"] = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Red Base -->
    <path android:fillColor="#CC0000" android:pathData="M0,0h640v480h-640z" />
{my_stripes}
    <!-- Blue Canton -->
    <path android:fillColor="#010066" android:pathData="M0,0h320v274h-320z" />
    <!-- Crescent Moon Base (cx=115, cy=137, r=70) -->
    <path android:fillColor="#FFCC00" android:pathData="{circle_path(115, 137, 70)}" />
    <!-- Crescent Moon Cutout (cx=135, cy=137, r=60) -->
    <path android:fillColor="#010066" android:pathData="{circle_path(135, 137, 60)}" />
    <!-- 14-Point Star -->
    <path android:fillColor="#FFCC00" android:pathData="{star_path(205, 137, 52, 22, points=14)}" />
</vector>
"""

# 41. ng (Nigeria)
flags["flag_ng.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Green Left -->
    <path android:fillColor="#008751" android:pathData="M0,0h213v480h-213z" />
    <!-- White Center -->
    <path android:fillColor="#FFFFFF" android:pathData="M213,0h214v480h-214z" />
    <!-- Green Right -->
    <path android:fillColor="#008751" android:pathData="M427,0h213v480h-213z" />
</vector>
"""

# 42. nl (Netherlands)
flags["flag_nl.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Red Top -->
    <path android:fillColor="#AE1C28" android:pathData="M0,0h640v160h-640z" />
    <!-- White Middle -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,160h640v160h-640z" />
    <!-- Blue Bottom -->
    <path android:fillColor="#21468B" android:pathData="M0,320h640v160h-640z" />
</vector>
"""

# 43. no (Norway)
flags["flag_no.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Red Field -->
    <path android:fillColor="#BA0C2F" android:pathData="M0,0h640v480h-640z" />
    <!-- White Cross Vertical -->
    <path android:fillColor="#FFFFFF" android:pathData="M160,0h120v480h-120z" />
    <!-- White Cross Horizontal -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,180h640v120h-640z" />
    <!-- Blue Inner Cross Vertical -->
    <path android:fillColor="#00205B" android:pathData="M190,0h60v480h-60z" />
    <!-- Blue Inner Cross Horizontal -->
    <path android:fillColor="#00205B" android:pathData="M0,210h640v60h-640z" />
</vector>
"""

# 44. nz (New Zealand)
nz_stars = f"""    <!-- Southern Cross 4 Stars (White outer + Red inner) -->
    <!-- Top -->
    <path android:fillColor="#FFFFFF" android:pathData="{star_path(480, 100, 22)}" />
    <path android:fillColor="#CC142B" android:pathData="{star_path(480, 100, 15)}" />
    <!-- Left -->
    <path android:fillColor="#FFFFFF" android:pathData="{star_path(415, 215, 20)}" />
    <path android:fillColor="#CC142B" android:pathData="{star_path(415, 215, 13)}" />
    <!-- Right -->
    <path android:fillColor="#FFFFFF" android:pathData="{star_path(545, 195, 20)}" />
    <path android:fillColor="#CC142B" android:pathData="{star_path(545, 195, 13)}" />
    <!-- Bottom -->
    <path android:fillColor="#FFFFFF" android:pathData="{star_path(480, 345, 26)}" />
    <path android:fillColor="#CC142B" android:pathData="{star_path(480, 345, 18)}" />"""

flags["flag_nz.xml"] = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Royal Blue Field -->
    <path android:fillColor="#00247D" android:pathData="M0,0h640v480h-640z" />
    <!-- Union Jack Canton -->
    <path android:fillColor="#00247D" android:pathData="M0,0h320v240h-320z" />
    <path android:strokeColor="#FFFFFF" android:strokeWidth="40" android:pathData="M0,0L320,240M320,0L0,240" />
    <path android:strokeColor="#CF142B" android:strokeWidth="24" android:pathData="M0,0L320,240M320,0L0,240" />
    <path android:strokeColor="#FFFFFF" android:strokeWidth="60" android:pathData="M160,0v240M0,120h320" />
    <path android:strokeColor="#CF142B" android:strokeWidth="36" android:pathData="M160,0v240M0,120h320" />
{nz_stars}
</vector>
"""

# 45. pa (Panama)
flags["flag_pa.xml"] = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Top-Left White with Blue Star -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,0h320v240h-320z" />
    <path android:fillColor="#005293" android:pathData="{star_path(160, 120, 48)}" />
    <!-- Top-Right Red -->
    <path android:fillColor="#D21034" android:pathData="M320,0h320v240h-320z" />
    <!-- Bottom-Left Blue -->
    <path android:fillColor="#005293" android:pathData="M0,240h320v240h-320z" />
    <!-- Bottom-Right White with Red Star -->
    <path android:fillColor="#FFFFFF" android:pathData="M320,240h320v240h-320z" />
    <path android:fillColor="#D21034" android:pathData="{star_path(480, 360, 48)}" />
</vector>
"""

# 46. pe (Peru)
flags["flag_pe.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Red Left -->
    <path android:fillColor="#D91023" android:pathData="M0,0h213v480h-213z" />
    <!-- White Center -->
    <path android:fillColor="#FFFFFF" android:pathData="M213,0h214v480h-214z" />
    <!-- Red Right -->
    <path android:fillColor="#D91023" android:pathData="M427,0h213v480h-213z" />
</vector>
"""

# 47. ph (Philippines)
ph_rays = []
for k in range(8):
    ang = k * math.pi / 4
    dx = 40 * math.cos(ang)
    dy = 40 * math.sin(ang)
    ph_rays.append(f'    <path android:strokeColor="#FCD116" android:strokeWidth="7" android:pathData="M{120+dx*0.6:.1f},{240+dy*0.6:.1f} L{120+dx:.1f},{240+dy:.1f}" />')

flags["flag_ph.xml"] = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Blue Top Half -->
    <path android:fillColor="#0038A8" android:pathData="M0,0h640v240h-640z" />
    <!-- Red Bottom Half -->
    <path android:fillColor="#CE1126" android:pathData="M0,240h640v240h-640z" />
    <!-- White Equilateral Triangle -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,0 L346,240 L0,480 Z" />
    <!-- Golden Sun Disc -->
    <path android:fillColor="#FCD116" android:pathData="{circle_path(120, 240, 24)}" />
{chr(10).join(ph_rays)}
    <!-- 3 Golden Stars -->
    <path android:fillColor="#FCD116" android:pathData="{star_path(42, 68, 16)}" />
    <path android:fillColor="#FCD116" android:pathData="{star_path(42, 412, 16)}" />
    <path android:fillColor="#FCD116" android:pathData="{star_path(275, 240, 16)}" />
</vector>
"""

# 48. pl (Poland)
flags["flag_pl.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- White Top -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,0h640v240h-640z" />
    <!-- Red Bottom -->
    <path android:fillColor="#DC143C" android:pathData="M0,240h640v240h-640z" />
</vector>
"""

# 49. pt (Portugal)
flags["flag_pt.xml"] = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Green Hoist (2/5) -->
    <path android:fillColor="#006600" android:pathData="M0,0h256v480h-256z" />
    <!-- Red Fly (3/5) -->
    <path android:fillColor="#FF0000" android:pathData="M256,0h384v480h-384z" />
    <!-- Yellow Armillary Sphere -->
    <path android:strokeColor="#FFCC00" android:strokeWidth="10" android:fillColor="#00000000" android:pathData="{circle_path(256, 240, 75)}" />
    <path android:strokeColor="#FFCC00" android:strokeWidth="6" android:pathData="M256,165v150 M181,240h150 M203,187l106,106" />
    <!-- Portuguese Shield -->
    <path android:fillColor="#FFFFFF" android:pathData="M231,205h50v50c0,20 -25,32 -25,32c0,0 -25,-12 -25,-32z" />
    <path android:strokeColor="#FF0000" android:strokeWidth="8" android:pathData="M231,205h50v50c0,20 -25,32 -25,32c0,0 -25,-12 -25,-32z" />
    <!-- Blue Quinas -->
    <path android:fillColor="#0038A8" android:pathData="{circle_path(256, 230, 6)} {circle_path(246, 245, 6)} {circle_path(266, 245, 6)}" />
</vector>
"""

# 50. py (Paraguay)
flags["flag_py.xml"] = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Red Top -->
    <path android:fillColor="#D52B1E" android:pathData="M0,0h640v160h-640z" />
    <!-- White Middle -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,160h640v160h-640z" />
    <!-- Blue Bottom -->
    <path android:fillColor="#0038A8" android:pathData="M0,320h640v160h-640z" />
    <!-- National Seal Center -->
    <path android:strokeColor="#2E7D32" android:strokeWidth="8" android:fillColor="#00000000" android:pathData="{circle_path(320, 240, 45)}" />
    <path android:fillColor="#FCD116" android:pathData="{star_path(320, 240, 20)}" />
</vector>
"""

# 51. ro (Romania)
flags["flag_ro.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Cobalt Blue Left -->
    <path android:fillColor="#002B7F" android:pathData="M0,0h213v480h-213z" />
    <!-- Chrome Yellow Center -->
    <path android:fillColor="#FCD116" android:pathData="M213,0h214v480h-214z" />
    <!-- Red Right -->
    <path android:fillColor="#CE1126" android:pathData="M427,0h213v480h-213z" />
</vector>
"""

# 52. rs (Serbia)
flags["flag_rs.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Red Top -->
    <path android:fillColor="#C6363C" android:pathData="M0,0h640v160h-640z" />
    <!-- Blue Middle -->
    <path android:fillColor="#0C4076" android:pathData="M0,160h640v160h-640z" />
    <!-- White Bottom -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,320h640v160h-640z" />
    <!-- Serbian Coat of Arms (Shifted Hoist) -->
    <!-- Crown -->
    <path android:fillColor="#FFD700" android:pathData="M220,135 L245,135 L250,155 L215,155 Z" />
    <!-- White Double-Headed Eagle -->
    <path android:fillColor="#FFFFFF" android:pathData="M232,165 L260,195 L275,185 L270,240 L250,270 L232,290 L214,270 L194,240 L189,185 L204,195 Z" />
    <!-- Red Inner Shield -->
    <path android:fillColor="#C6363C" android:pathData="M217,215h30v35c0,12 -15,20 -15,20c0,0 -15,-8 -15,-20z" />
    <path android:fillColor="#FFFFFF" android:pathData="M230,220h4v25h-4z M222,230h20v4h-20z" />
</vector>
"""

# 53. ru (Russia)
flags["flag_ru.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- White Top -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,0h640v160h-640z" />
    <!-- Blue Middle -->
    <path android:fillColor="#0039A6" android:pathData="M0,160h640v160h-640z" />
    <!-- Red Bottom -->
    <path android:fillColor="#D52B1E" android:pathData="M0,320h640v160h-640z" />
</vector>
"""

# 54. se (Sweden)
flags["flag_se.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Medium Blue Field -->
    <path android:fillColor="#006AA7" android:pathData="M0,0h640v480h-640z" />
    <!-- Yellow Nordic Cross Vertical -->
    <path android:fillColor="#FECC00" android:pathData="M180,0h80v480h-80z" />
    <!-- Yellow Nordic Cross Horizontal -->
    <path android:fillColor="#FECC00" android:pathData="M0,195h640v90h-640z" />
</vector>
"""

# 55. si (Slovenia)
flags["flag_si.xml"] = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- White Top -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,0h640v160h-640z" />
    <!-- Blue Middle -->
    <path android:fillColor="#005CE6" android:pathData="M0,160h640v160h-640z" />
    <!-- Red Bottom -->
    <path android:fillColor="#ED1C24" android:pathData="M0,320h640v160h-640z" />
    <!-- Coat of Arms at Upper Hoist -->
    <path android:fillColor="#005CE6" android:pathData="M135,100h50v55c0,22 -25,35 -25,35c0,0 -25,-13 -25,-35z" />
    <path android:strokeColor="#ED1C24" android:strokeWidth="5" android:pathData="M135,100h50v55c0,22 -25,35 -25,35c0,0 -25,-13 -25,-35z" />
    <!-- Triglav White Peaks -->
    <path android:fillColor="#FFFFFF" android:pathData="M140,165 L150,140 L160,150 L170,135 L180,165 Z" />
    <!-- 3 Golden Stars -->
    <path android:fillColor="#FFD700" android:pathData="{star_path(160, 115, 6)} {star_path(150, 125, 5)} {star_path(170, 125, 5)}" />
</vector>
"""

# 56. sk (Slovakia)
flags["flag_sk.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- White Top -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,0h640v160h-640z" />
    <!-- Blue Middle -->
    <path android:fillColor="#0B4EA2" android:pathData="M0,160h640v160h-640z" />
    <!-- Red Bottom -->
    <path android:fillColor="#EE1C25" android:pathData="M0,320h640v160h-640z" />
    <!-- Coat of Arms Shield at Hoist -->
    <path android:fillColor="#EE1C25" android:pathData="M150,160h60v70c0,30 -30,45 -30,45c0,0 -30,-15 -30,-45z" />
    <path android:strokeColor="#FFFFFF" android:strokeWidth="6" android:pathData="M150,160h60v70c0,30 -30,45 -30,45c0,0 -30,-15 -30,-45z" />
    <!-- Blue Three Hills -->
    <path android:fillColor="#0B4EA2" android:pathData="M155,255 C165,235 175,235 180,245 C185,235 195,235 205,255 Z" />
    <!-- White Patriarchal Cross -->
    <path android:fillColor="#FFFFFF" android:pathData="M176,175h8v75h-8z M164,190h32v8h-32z M160,208h40v8h-40z" />
</vector>
"""

# 57. sv (El Salvador)
flags["flag_sv.xml"] = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Cobalt Blue Top -->
    <path android:fillColor="#0F47AF" android:pathData="M0,0h640v160h-640z" />
    <!-- White Middle -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,160h640v160h-640z" />
    <!-- Cobalt Blue Bottom -->
    <path android:fillColor="#0F47AF" android:pathData="M0,320h640v160h-640z" />
    <!-- Golden Triangle Coat of Arms -->
    <path android:strokeColor="#FCD116" android:strokeWidth="8" android:fillColor="#00000000" android:pathData="M320,190 L365,270 L275,270 Z" />
    <path android:fillColor="#2E7D32" android:pathData="M285,265 L300,245 L320,255 L335,245 L355,265 Z" />
    <path android:strokeColor="#2E7D32" android:strokeWidth="6" android:pathData="{circle_path(320, 240, 52)}" />
</vector>
"""

# 58. th (Thailand)
flags["flag_th.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Red Top -->
    <path android:fillColor="#A51931" android:pathData="M0,0h640v80h-640z" />
    <!-- White Top Stripe -->
    <path android:fillColor="#F4F5F8" android:pathData="M0,80h640v80h-640z" />
    <!-- Blue Middle Band (Double Width) -->
    <path android:fillColor="#2D2A4A" android:pathData="M0,160h640v160h-640z" />
    <!-- White Bottom Stripe -->
    <path android:fillColor="#F4F5F8" android:pathData="M0,320h640v80h-640z" />
    <!-- Red Bottom -->
    <path android:fillColor="#A51931" android:pathData="M0,400h640v80h-640z" />
</vector>
"""

# 59. tr (Turkey)
flags["flag_tr.xml"] = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Red Field -->
    <path android:fillColor="#E30A17" android:pathData="M0,0h640v480h-640z" />
    <!-- White Crescent Base Circle (cx=250, cy=240, r=120) -->
    <path android:fillColor="#FFFFFF" android:pathData="{circle_path(250, 240, 120)}" />
    <!-- Red Crescent Cutout Circle (cx=280, cy=240, r=96) -->
    <path android:fillColor="#E30A17" android:pathData="{circle_path(280, 240, 96)}" />
    <!-- White 5-Pointed Star -->
    <path android:fillColor="#FFFFFF" android:pathData="{star_path(375, 240, 48, start_angle=-math.pi/2)}" />
</vector>
"""

# 60. ua (Ukraine)
flags["flag_ua.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Azure Blue Top -->
    <path android:fillColor="#0057B7" android:pathData="M0,0h640v240h-640z" />
    <!-- Yellow Bottom -->
    <path android:fillColor="#FFDD00" android:pathData="M0,240h640v240h-640z" />
</vector>
"""

# 61. uy (Uruguay)
uy_stripes = "\n".join([f'    <path android:fillColor="#0038A8" android:pathData="M0,{int(k*53.33)}h640v{int(53.33)}h-640z" />' for k in [1, 3, 5, 7]])

flags["flag_uy.xml"] = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- White Field -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,0h640v480h-640z" />
{uy_stripes}
    <!-- White Canton -->
    <path android:fillColor="#FFFFFF" android:pathData="M0,0h240v267h-240z" />
    <!-- Golden Sun of May in Canton -->
    <path android:fillColor="#FCD116" android:pathData="{circle_path(120, 133, 35)}" />
    <path android:strokeColor="#FCD116" android:strokeWidth="5" android:pathData="M120,78v20 M120,168v20 M65,133h20 M155,133h20 M81,94l14,14 M145,158l14,14 M159,94l-14,14 M81,172l14,-14" />
</vector>
"""

# 62. vn (Vietnam)
flags["flag_vn.xml"] = f"""<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Red Field -->
    <path android:fillColor="#DA251D" android:pathData="M0,0h640v480h-640z" />
    <!-- Yellow Five-Pointed Star -->
    <path android:fillColor="#FFFF00" android:pathData="{star_path(320, 240, 130, 50)}" />
</vector>
"""

# 63. za (South Africa)
flags["flag_za.xml"] = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="52dp"
    android:height="38dp"
    android:viewportWidth="640"
    android:viewportHeight="480">
    <!-- Chili Red Top -->
    <path android:fillColor="#E03C31" android:pathData="M0,0h640v240h-640z" />
    <!-- Blue Bottom -->
    <path android:fillColor="#001489" android:pathData="M0,240h640v240h-640z" />
    <!-- White Y Pall Base -->
    <path android:strokeColor="#FFFFFF" android:strokeWidth="150" android:pathData="M0,0 L240,240 H640 M0,480 L240,240" />
    <!-- Green Y Pall -->
    <path android:strokeColor="#007749" android:strokeWidth="90" android:pathData="M0,0 L240,240 H640 M0,480 L240,240" />
    <!-- Black Triangle at Hoist -->
    <path android:fillColor="#000000" android:pathData="M0,75 L165,240 L0,405 Z" />
    <!-- Yellow Triangle Border -->
    <path android:strokeColor="#FFB81C" android:strokeWidth="24" android:pathData="M0,75 L165,240 L0,405" />
</vector>
"""

def main():
    print(f"Generating {len(flags)} flag files...")
    for filename, content in flags.items():
        filepath = os.path.join(OUT_DIR, filename)
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"  Wrote {filename}")
    print("Done!")

if __name__ == "__main__":
    main()
