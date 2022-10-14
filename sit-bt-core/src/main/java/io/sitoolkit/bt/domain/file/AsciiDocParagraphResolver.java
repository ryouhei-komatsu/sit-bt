package io.sitoolkit.bt.domain.file;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AsciiDocParagraphResolver implements ParagraphResolver {

  private static final String VERTICAL_BAR_REPLACEMENT = "<VB>";

  private final Pattern escapePrefixPattern = Pattern.compile("^\\.");
  private final Pattern consecutiveEqualsPrefixPattern =
      Pattern.compile("^(= ){2,6}(.*)", Pattern.DOTALL);
  private final Pattern hatPrefixPattern =
      Pattern.compile("(.*)(\\^)( )(.*)( )(\\^)(.*)", Pattern.DOTALL);
  private final Pattern tildePrefixPattern =
      Pattern.compile("(.*)(\\~)( )(.*)( )(\\~)(.*)", Pattern.DOTALL);
  private final Pattern footNotePrefixPattern =
      Pattern.compile("^(TIP|IMPORTANT|CAUTION|NOTE)( +)(:.*)", Pattern.DOTALL);
  private final Pattern warningPrefixPattern =
      Pattern.compile("^(WARNING)( +)(-.*)", Pattern.DOTALL);

  @Override
  public List<Paragraph> resolve(Path file) {

    List<String> lines = null;

    try {
      lines = Files.readAllLines(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    List<Paragraph> paragraphs = new ArrayList<>();
    Paragraph paragraph = new Paragraph();

    for (String line : lines) {

      if (paragraph.isIgnored() && checkIgnoredLine(line)) {
        paragraph.append(line);
        paragraphs.add(paragraph);
        paragraph = new Paragraph();
        continue;
      }

      // 空行は翻訳しない
      if (line.isBlank()) {
        paragraphs.add(paragraph);
        paragraph = new Paragraph();
        paragraph.append(line);
        paragraph.setIgnored(true);
        paragraphs.add(paragraph);
        paragraph = new Paragraph();
        continue;
      }

      // . から始まる場合、翻訳時に . が除去されるため、翻訳前に退避する
      if (line.startsWith(".")) {
        paragraph.setEscapePrefix(findPrefix(line));
      }

      // 翻訳時に | が除去されるため、翻訳前に置換する
      if (line.startsWith("|")) {
        line = line.replaceAll("\\|", VERTICAL_BAR_REPLACEMENT);
      }

      paragraph.append(line);

      // 次の----までの範囲は翻訳しない
      if (checkIgnoredLine(line)) {
        paragraph.setIgnored(true);
      }
    }
    paragraphs.add(paragraph);

    return paragraphs;
  }

  private boolean checkIgnoredLine(String line) {
    boolean result = false;
    if (line.startsWith("----") || line.startsWith("....") || line.startsWith("++++")) {
      result = true;
    }
    return result;
  }

  private String findPrefix(String line) {
    Matcher matcher = escapePrefixPattern.matcher(line);
    matcher.find();
    return matcher.group();
  }

  @Override
  public String correct(Paragraph paragraph) {
    return correct(
        paragraph.getText(),
        paragraph.getTranslatedText(),
        paragraph.getEscapePrefix(),
        paragraph.isIgnored());
  }

  public String correct(
      String originalText, String translatedText, String escapePrefix, boolean ignored) {

    if (ignored) {
      return originalText;
    }

    // 翻訳時に発生するAsciiDoc記法の崩れを調整する
    translatedText = adjust(translatedText);

    if (escapePrefix.isEmpty()) {
      return translatedText;
    }

    StringBuilder correctText = new StringBuilder();
    correctText.append(escapePrefix);
    correctText.append(translatedText);
    return correctText.toString();
  }

  private String adjust(String translatedText) {
    // 文中の置換用文字列を置換する
    translatedText = replaceSpecificText(translatedText);
    // 記号に囲まれた文字の両端の空白を除去する
    translatedText = removeSpaceOnBothEnds(translatedText);
    // 記号と文字の間の空白を除去する
    translatedText = removeSpaceBetweenSynmbolAndText(translatedText);
    // 記号と文字の間に空白が1つになるように調整する
    translatedText = addSpaceBeforeSymbol(translatedText);

    return translatedText;
  }

  private String replaceSpecificText(String translatedText) {
    // 文中の置換用文字列を「|」に置換する
    if (translatedText.contains(VERTICAL_BAR_REPLACEMENT)) {
      translatedText = translatedText.replaceAll(VERTICAL_BAR_REPLACEMENT, "|");
    }
    return translatedText;
  }

  private String removeSpaceOnBothEnds(String translatedText) {
    // 文字の両端の空白を除去する
    String replacement = "$1$2$4$6$7";
    Matcher hat = hatPrefixPattern.matcher(translatedText);
    if (hat.matches()) {
      translatedText = hat.replaceAll(replacement);
    }
    Matcher tilde = tildePrefixPattern.matcher(translatedText);
    if (tilde.matches()) {
      translatedText = tilde.replaceAll(replacement);
    }
    return translatedText;
  }

  private String removeSpaceBetweenSynmbolAndText(String translatedText) {
    // 翻訳APIは「NOTE: XXX」を「NOTE : XXX」と翻訳するため、不要なスペースを除去する
    String replacement = "$1$3";
    Matcher footnote = footNotePrefixPattern.matcher(translatedText);
    if (footnote.matches()) {
      translatedText = footnote.replaceAll(replacement);
    }
    // 翻訳APIは「WARNING: XXX」を「WARNING - XXX」と翻訳するため、
    // 不要なスペースを削除した上で「-」を「:」に置換する
    Matcher warning = warningPrefixPattern.matcher(translatedText);
    if (warning.matches()) {
      translatedText = warning.replaceAll(replacement).replaceFirst("-", ":");
    }
    return translatedText;
  }

  private String addSpaceBeforeSymbol(String translatedText) {
    // 翻訳APIはイコールを「 = 」と翻訳するため、
    // 「=」と後続の文字列の間にのみ半角スペースを挿入するように調整する
    if (consecutiveEqualsPrefixPattern.matcher(translatedText).matches()) {
      translatedText = translatedText.replaceAll("= ", "=").replaceAll("(=*)([^=]*)", "$1 $2");
    }
    return translatedText;
  }
}
