# =====================================================================
# DeepseekPet Premium — ProGuard 混淆配置
# =====================================================================
# 使用方式：mvn package -Pobfuscate
# 产物：target/DeepseekPet-Premium-<version>-obf.jar
#
# 目标：混淆 premium 专属功能实现（premium.features.*），防止他人
# 轻易反编译付费逻辑。共享代码（free 也有）保持可读，不属于秘密。
# =====================================================================

# 只做「改名」混淆，不删除、不优化，避免误删反射调用的代码
-dontshrink
-dontoptimize
-dontpreverify

# 保留行号 / 源文件信息（方便排错，代价是暴露一点结构，可自行去掉）
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*

# ---------------------------------------------------------------------
# 1) 重定位后的 Gson 库：完整保留（Gson 靠反射序列化）
# ---------------------------------------------------------------------
-keep class org.buzizi.deepseekpet.libs.** { *; }

# ---------------------------------------------------------------------
# 2) plugin.yml 主类
# ---------------------------------------------------------------------
-keep class org.buzizi.deepseekpet.DeepseekPet { public protected *; }
-keep class org.buzizi.deepseekpet.premium.DeepseekPetPremium { public protected *; }

# ---------------------------------------------------------------------
# 3) 共享代码：保留（free 与 premium 都有，含 Gson 数据类与事件入口）
# ---------------------------------------------------------------------
-keep class org.buzizi.deepseekpet.DeepseekPetCore { *; }
-keep class org.buzizi.deepseekpet.core.** { *; }
-keep class org.buzizi.deepseekpet.ai.** { *; }
-keep class org.buzizi.deepseekpet.gui.** { *; }
-keep class org.buzizi.deepseekpet.command.** { *; }
-keep class org.buzizi.deepseekpet.listener.** { *; }
-keep class org.buzizi.deepseekpet.util.** { *; }

# ---------------------------------------------------------------------
# 4) premium 的接口与注册表：保留（作为扩展点被主类直接调用）
# ---------------------------------------------------------------------
-keep interface org.buzizi.deepseekpet.premium.PremiumFeature { *; }
-keep class org.buzizi.deepseekpet.premium.PremiumFeatureRegistry { *; }

# ---------------------------------------------------------------------
# 5) Bukkit 事件处理器：必须保留（Bukkit 靠注解反射查找方法）
# ---------------------------------------------------------------------
-keepclassmembers class * {
    @org.bukkit.event.EventHandler <methods>;
}

# ---------------------------------------------------------------------
# 说明：org.buzizi.deepseekpet.premium.features.* 未加入 keep，
# ProGuard 会重命名其类名 / 私有成员，达到混淆付费逻辑的目的。
# 若某个 premium 功能类被反射调用，请在此追加 keep 规则。
# ---------------------------------------------------------------------
