/**
 * Type Assist -- onboarding screen.
 * Walks the user through enabling and switching to the custom keyboard.
 *
 * @format
 */

import React, {useRef, useEffect, useState} from 'react';
import {
  Animated,
  Dimensions,
  Linking,
  Platform,
  Pressable,
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import LinearGradient from 'react-native-linear-gradient';

const {width: SCREEN_WIDTH} = Dimensions.get('window');

// ─── Intent helpers ──────────────────────────────────────────────────────────

function openInputMethodSettings() {
  if (Platform.OS !== 'android') {
    return;
  }
  Linking.sendIntent('android.settings.INPUT_METHOD_SETTINGS');
}

function openTypingHistory() {
  if (Platform.OS !== 'android') {
    return;
  }
  Linking.sendIntent('com.aikeyboard.assistant.SHOW_TYPING_HISTORY');
}

function openKeyboardSettings() {
  if (Platform.OS !== 'android') {
    return;
  }
  Linking.sendIntent('com.aikeyboard.assistant.SHOW_KEYBOARD_SETTINGS');
}

// ─── Feature data ────────────────────────────────────────────────────────────

const FEATURES = [
  {
    emoji: '🌐',
    title: 'Translate to 50+\nLanguages On Keyboard',
    description: 'Translate your text instantly while you type. Supports 50+ languages.',
    gradient: ['#7C3AED', '#A855F7', '#C084FC'],
    iconBg: '#6D28D9',
    demo: {
      label: 'Translate',
      from: 'Hello, how are you?',
      to: 'Bonjour, comment allez-vous?',
      lang: '🇫🇷 French',
    },
  },
  {
    emoji: '🎭',
    title: 'Type In Your Tone.\nWe Adjust to Perfect Tone',
    description: 'Change between Professional, Funny, Casual, and more tones.',
    gradient: ['#6D28D9', '#7C3AED', '#8B5CF6'],
    iconBg: '#5B21B6',
    demo: {
      label: 'Change Tone',
      tones: ['👔 Professional', '🤣 Funny', '😎 Casual'],
      original: 'Are you coming to the movie today?',
      results: [
        "Y'all coming to the movie today?",
        'You gonna make it to the movie today?',
      ],
    },
  },
  {
    emoji: '✅',
    title: 'Fix Grammar\nOne Click to Perfection',
    description: 'Instant grammar correction with one tap. Never send a typo again.',
    gradient: ['#8B5CF6', '#A855F7', '#D946EF'],
    iconBg: '#7C3AED',
    demo: {
      label: 'Grammar Check',
      before: 'Why u not tell me meeting cancel',
      after: "Why didn't you tell me the meeting was canceled?",
      corrections: [
        {wrong: 'u', right: "didn't you"},
        {wrong: 'cancel', right: 'was canceled?'},
      ],
    },
  },
  {
    emoji: '⌨️',
    title: 'AI-Powered\nSmart Keyboard',
    description: 'Smart replies, auto-complete, and context-aware suggestions.',
    gradient: ['#5B21B6', '#7C3AED', '#A855F7'],
    iconBg: '#4C1D95',
    demo: {
      label: 'Smart Features',
      features: ['✨ Smart Reply', '🔮 Auto-Complete', '🧠 Context-Aware'],
    },
  },
];

// ─── Animated Feature Card ──────────────────────────────────────────────────

function FeatureCard({
  feature,
  index,
}: {
  feature: (typeof FEATURES)[number];
  index: number;
}) {
  const fadeAnim = useRef(new Animated.Value(0)).current;
  const slideAnim = useRef(new Animated.Value(40)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(fadeAnim, {
        toValue: 1,
        duration: 600,
        delay: index * 200,
        useNativeDriver: true,
      }),
      Animated.timing(slideAnim, {
        toValue: 0,
        duration: 600,
        delay: index * 200,
        useNativeDriver: true,
      }),
    ]).start();
  }, [fadeAnim, slideAnim, index]);

  return (
    <Animated.View
      style={[
        styles.featureCard,
        {opacity: fadeAnim, transform: [{translateY: slideAnim}]},
      ]}>
      <LinearGradient
        colors={feature.gradient}
        start={{x: 0, y: 0}}
        end={{x: 1, y: 1}}
        style={styles.featureGradient}>
        {/* Feature demo area */}
        <View style={styles.featureDemoArea}>
          <View style={styles.featureIconRow}>
            <View style={[styles.featureIconCircle, {backgroundColor: feature.iconBg}]}>
              <Text style={styles.featureEmoji}>{feature.emoji}</Text>
            </View>
            <Text style={styles.featureDemoLabel}>{feature.demo.label}</Text>
          </View>

          {/* Demo content based on type */}
          {'from' in feature.demo && (
            <View style={styles.demoContent}>
              <View style={styles.demoTextBubble}>
                <Text style={styles.demoTextLight}>{feature.demo.from}</Text>
              </View>
              <Text style={styles.demoArrow}>↓</Text>
              <View style={[styles.demoTextBubble, styles.demoResultBubble]}>
                <Text style={styles.demoLangBadge}>{feature.demo.lang}</Text>
                <Text style={styles.demoTextWhite}>{feature.demo.to}</Text>
              </View>
            </View>
          )}

          {'tones' in feature.demo && (
            <View style={styles.demoContent}>
              <View style={styles.tonePillsRow}>
                {feature.demo.tones?.map((tone, i) => (
                  <View key={i} style={styles.tonePill}>
                    <Text style={styles.tonePillText}>{tone}</Text>
                  </View>
                ))}
              </View>
              {feature.demo.results?.map((result, i) => (
                <View key={i} style={styles.demoTextBubble}>
                  <Text style={styles.demoTextLight}>{result}</Text>
                </View>
              ))}
            </View>
          )}

          {'corrections' in feature.demo && (
            <View style={styles.demoContent}>
              <View style={styles.grammarBox}>
                <Text style={styles.grammarBefore}>{feature.demo.before}</Text>
                <View style={styles.grammarDivider} />
                <Text style={styles.grammarAfter}>{feature.demo.after}</Text>
              </View>
            </View>
          )}

          {'features' in feature.demo && (
            <View style={styles.demoContent}>
              {feature.demo.features?.map((feat, i) => (
                <View key={i} style={styles.smartFeaturePill}>
                  <Text style={styles.smartFeatureText}>{feat}</Text>
                </View>
              ))}
            </View>
          )}
        </View>

        {/* Feature title */}
        <Text style={styles.featureTitle}>{feature.title}</Text>
        <Text style={styles.featureDescription}>{feature.description}</Text>
      </LinearGradient>
    </Animated.View>
  );
}

// ─── Dot Indicator ───────────────────────────────────────────────────────────

function DotIndicator({total, active}: {total: number; active: number}) {
  return (
    <View style={styles.dotRow}>
      {Array.from({length: total}).map((_, i) => (
        <View
          key={i}
          style={[
            styles.dot,
            i === active ? styles.dotActive : styles.dotInactive,
          ]}
        />
      ))}
    </View>
  );
}

// ─── Setup Step ──────────────────────────────────────────────────────────────

function SetupStep({
  number,
  title,
  description,
  buttonText,
  onPress,
}: {
  number: string;
  title: string;
  description: string;
  buttonText?: string;
  onPress?: () => void;
}) {
  return (
    <View style={styles.setupStep}>
      <LinearGradient
        colors={['#7C3AED', '#A855F7']}
        style={styles.stepNumberCircle}>
        <Text style={styles.stepNumberText}>{number}</Text>
      </LinearGradient>
      <View style={styles.stepContent}>
        <Text style={styles.setupStepTitle}>{title}</Text>
        <Text style={styles.setupStepDesc}>{description}</Text>
        {buttonText && onPress && (
          <Pressable
            style={({pressed}) => [
              styles.setupButton,
              pressed && styles.setupButtonPressed,
            ]}
            onPress={onPress}>
            <LinearGradient
              colors={['#7C3AED', '#A855F7']}
              start={{x: 0, y: 0}}
              end={{x: 1, y: 0}}
              style={styles.setupButtonGradient}>
              <Text style={styles.setupButtonText}>{buttonText}</Text>
            </LinearGradient>
          </Pressable>
        )}
      </View>
    </View>
  );
}

// ─── Settings Row ────────────────────────────────────────────────────────────

function SettingsRow({
  icon,
  label,
  onPress,
}: {
  icon: string;
  label: string;
  onPress: () => void;
}) {
  return (
    <Pressable
      style={({pressed}) => [
        styles.settingsRow,
        pressed && styles.settingsRowPressed,
      ]}
      onPress={onPress}>
      <View style={styles.settingsIconCircle}>
        <Text style={styles.settingsIcon}>{icon}</Text>
      </View>
      <Text style={styles.settingsLabel}>{label}</Text>
      <Text style={styles.settingsArrow}>›</Text>
    </Pressable>
  );
}

// ─── Main App ────────────────────────────────────────────────────────────────

function App() {
  const [activeFeature, setActiveFeature] = useState(0);
  const heroFade = useRef(new Animated.Value(0)).current;
  const heroScale = useRef(new Animated.Value(0.9)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(heroFade, {
        toValue: 1,
        duration: 800,
        useNativeDriver: true,
      }),
      Animated.spring(heroScale, {
        toValue: 1,
        friction: 8,
        tension: 40,
        useNativeDriver: true,
      }),
    ]).start();
  }, [heroFade, heroScale]);

  const handleScroll = (event: any) => {
    const scrollX = event.nativeEvent.contentOffset.x;
    const cardWidth = SCREEN_WIDTH - 48;
    const index = Math.round(scrollX / cardWidth);
    setActiveFeature(Math.min(index, FEATURES.length - 1));
  };

  return (
    <View style={styles.container}>
      <StatusBar barStyle="light-content" translucent backgroundColor="transparent" />
      <LinearGradient
        colors={['#4C1D95', '#6D28D9', '#7C3AED', '#5B21B6']}
        start={{x: 0, y: 0}}
        end={{x: 1, y: 1}}
        style={StyleSheet.absoluteFill}
      />

      {/* Decorative diagonal rays */}
      <View style={styles.rayContainer}>
        <View style={[styles.ray, styles.ray1]} />
        <View style={[styles.ray, styles.ray2]} />
        <View style={[styles.ray, styles.ray3]} />
      </View>

      <SafeAreaView style={styles.safeArea}>
        <ScrollView
          style={styles.scrollView}
          contentContainerStyle={styles.scrollContent}
          showsVerticalScrollIndicator={false}>

          {/* ── Hero ────────────────────────────────────────────── */}
          <Animated.View
            style={[
              styles.heroSection,
              {opacity: heroFade, transform: [{scale: heroScale}]},
            ]}>
            <View style={styles.heroIconWrap}>
              <LinearGradient
                colors={['#A855F7', '#D946EF']}
                style={styles.heroIconGradient}>
                <Text style={styles.heroIcon}>⌨️</Text>
              </LinearGradient>
            </View>
            <Text style={styles.heroTitle}>Type Assist</Text>
            <Text style={styles.heroSubtitle}>
              AI-powered keyboard with grammar fixes,{'\n'}tone rewrites, smart
              replies & translations
            </Text>
          </Animated.View>

          {/* ── Feature Showcase (Horizontal Scroll) ────────────── */}
          <View style={styles.featureSection}>
            <Text style={styles.sectionLabel}>✨ FEATURES</Text>
            <ScrollView
              horizontal
              pagingEnabled={false}
              snapToInterval={SCREEN_WIDTH - 48}
              decelerationRate="fast"
              showsHorizontalScrollIndicator={false}
              contentContainerStyle={styles.featureScrollContent}
              onMomentumScrollEnd={handleScroll}>
              {FEATURES.map((feature, i) => (
                <FeatureCard key={i} feature={feature} index={i} />
              ))}
            </ScrollView>
            <DotIndicator total={FEATURES.length} active={activeFeature} />
          </View>

          {/* ── Setup Steps ─────────────────────────────────────── */}
          <View style={styles.setupSection}>
            <Text style={styles.sectionLabel}>🚀 GET STARTED</Text>
            <View style={styles.setupCard}>
              <SetupStep
                number="1"
                title="Enable the keyboard"
                description={'Turn on "Type Assist" in your system\'s keyboard list.'}
                buttonText="Open Keyboard Settings"
                onPress={openInputMethodSettings}
              />
              <View style={styles.stepDivider} />
              <SetupStep
                number="2"
                title="Switch to it"
                description='While typing in any app, tap the keyboard/globe icon and choose "Type Assist".'
              />
            </View>
          </View>

          {/* ── Quick Settings ──────────────────────────────────── */}
          <View style={styles.settingsSection}>
            <Text style={styles.sectionLabel}>⚙️ SETTINGS</Text>
            <View style={styles.settingsCard}>
              <SettingsRow
                icon="🎨"
                label="Keyboard Theme & Font"
                onPress={openKeyboardSettings}
              />
              <View style={styles.settingsDivider} />
              <SettingsRow
                icon="📝"
                label="View Typing History"
                onPress={openTypingHistory}
              />
            </View>
          </View>

          {/* ── Footer ──────────────────────────────────────────── */}
          <View style={styles.footer}>
            <Text style={styles.footerText}>
              Made with 💜 for better typing
            </Text>
          </View>
        </ScrollView>
      </SafeAreaView>
    </View>
  );
}

// ─── Styles ──────────────────────────────────────────────────────────────────

const CARD_WIDTH = SCREEN_WIDTH - 48;

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#4C1D95',
  },
  safeArea: {
    flex: 1,
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    paddingBottom: 40,
  },

  // ── Decorative rays ────────────────────────────────────
  rayContainer: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    overflow: 'hidden',
  },
  ray: {
    position: 'absolute',
    backgroundColor: 'rgba(255,255,255,0.03)',
  },
  ray1: {
    width: 300,
    height: 600,
    top: -100,
    left: -50,
    transform: [{rotate: '-35deg'}],
    borderRadius: 100,
  },
  ray2: {
    width: 200,
    height: 500,
    top: 200,
    right: -80,
    transform: [{rotate: '25deg'}],
    borderRadius: 80,
  },
  ray3: {
    width: 250,
    height: 400,
    bottom: -50,
    left: 60,
    transform: [{rotate: '-20deg'}],
    borderRadius: 60,
  },

  // ── Hero ───────────────────────────────────────────────
  heroSection: {
    alignItems: 'center',
    paddingTop: 60,
    paddingBottom: 32,
    paddingHorizontal: 24,
  },
  heroIconWrap: {
    marginBottom: 16,
    shadowColor: '#D946EF',
    shadowOffset: {width: 0, height: 8},
    shadowOpacity: 0.4,
    shadowRadius: 24,
    elevation: 12,
  },
  heroIconGradient: {
    width: 80,
    height: 80,
    borderRadius: 24,
    justifyContent: 'center',
    alignItems: 'center',
  },
  heroIcon: {
    fontSize: 36,
  },
  heroTitle: {
    fontSize: 36,
    fontWeight: '800',
    color: '#FFFFFF',
    letterSpacing: -0.5,
    marginBottom: 8,
  },
  heroSubtitle: {
    fontSize: 16,
    color: 'rgba(255,255,255,0.75)',
    textAlign: 'center',
    lineHeight: 24,
    maxWidth: 320,
  },

  // ── Feature Section ────────────────────────────────────
  featureSection: {
    paddingTop: 8,
  },
  sectionLabel: {
    fontSize: 12,
    fontWeight: '700',
    color: 'rgba(255,255,255,0.5)',
    letterSpacing: 1.5,
    marginBottom: 16,
    paddingHorizontal: 24,
  },
  featureScrollContent: {
    paddingHorizontal: 24,
  },
  featureCard: {
    width: CARD_WIDTH,
    marginRight: 16,
    borderRadius: 24,
    overflow: 'hidden',
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 12},
    shadowOpacity: 0.3,
    shadowRadius: 20,
    elevation: 10,
  },
  featureGradient: {
    padding: 24,
    paddingBottom: 28,
    minHeight: 380,
  },

  // ── Feature demo area ──────────────────────────────────
  featureDemoArea: {
    backgroundColor: 'rgba(0,0,0,0.2)',
    borderRadius: 16,
    padding: 16,
    marginBottom: 20,
  },
  featureIconRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  featureIconCircle: {
    width: 36,
    height: 36,
    borderRadius: 18,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 10,
  },
  featureEmoji: {
    fontSize: 18,
  },
  featureDemoLabel: {
    fontSize: 15,
    fontWeight: '700',
    color: '#FFFFFF',
  },

  // ── Demo content ───────────────────────────────────────
  demoContent: {
    gap: 8,
  },
  demoTextBubble: {
    backgroundColor: 'rgba(255,255,255,0.12)',
    borderRadius: 12,
    padding: 12,
  },
  demoResultBubble: {
    backgroundColor: 'rgba(255,255,255,0.2)',
  },
  demoTextLight: {
    fontSize: 14,
    color: 'rgba(255,255,255,0.9)',
    lineHeight: 20,
  },
  demoTextWhite: {
    fontSize: 14,
    color: '#FFFFFF',
    fontWeight: '600',
    lineHeight: 20,
  },
  demoArrow: {
    textAlign: 'center',
    color: 'rgba(255,255,255,0.5)',
    fontSize: 18,
  },
  demoLangBadge: {
    fontSize: 11,
    color: 'rgba(255,255,255,0.7)',
    fontWeight: '600',
    marginBottom: 4,
  },

  // ── Tone pills ─────────────────────────────────────────
  tonePillsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginBottom: 4,
  },
  tonePill: {
    backgroundColor: 'rgba(255,255,255,0.15)',
    borderRadius: 20,
    paddingVertical: 6,
    paddingHorizontal: 12,
  },
  tonePillText: {
    fontSize: 12,
    color: '#FFFFFF',
    fontWeight: '600',
  },

  // ── Grammar demo ───────────────────────────────────────
  grammarBox: {
    backgroundColor: 'rgba(255,255,255,0.12)',
    borderRadius: 12,
    padding: 14,
  },
  grammarBefore: {
    fontSize: 14,
    color: 'rgba(255,255,255,0.6)',
    textDecorationLine: 'line-through',
    marginBottom: 8,
    lineHeight: 20,
  },
  grammarDivider: {
    height: 1,
    backgroundColor: 'rgba(255,255,255,0.15)',
    marginBottom: 8,
  },
  grammarAfter: {
    fontSize: 14,
    color: '#4ADE80',
    fontWeight: '600',
    lineHeight: 20,
  },

  // ── Smart features ─────────────────────────────────────
  smartFeaturePill: {
    backgroundColor: 'rgba(255,255,255,0.12)',
    borderRadius: 12,
    paddingVertical: 12,
    paddingHorizontal: 16,
  },
  smartFeatureText: {
    fontSize: 14,
    color: '#FFFFFF',
    fontWeight: '600',
  },

  // ── Feature card text ──────────────────────────────────
  featureTitle: {
    fontSize: 24,
    fontWeight: '800',
    color: '#FFFFFF',
    lineHeight: 32,
    marginBottom: 8,
  },
  featureDescription: {
    fontSize: 14,
    color: 'rgba(255,255,255,0.7)',
    lineHeight: 20,
  },

  // ── Dot indicator ──────────────────────────────────────
  dotRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    marginTop: 20,
    gap: 8,
  },
  dot: {
    height: 8,
    borderRadius: 4,
  },
  dotActive: {
    width: 24,
    backgroundColor: '#FFFFFF',
  },
  dotInactive: {
    width: 8,
    backgroundColor: 'rgba(255,255,255,0.3)',
  },

  // ── Setup Section ──────────────────────────────────────
  setupSection: {
    paddingHorizontal: 24,
    paddingTop: 40,
  },
  setupCard: {
    backgroundColor: 'rgba(255,255,255,0.08)',
    borderRadius: 20,
    padding: 20,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.1)',
  },
  setupStep: {
    flexDirection: 'row',
    alignItems: 'flex-start',
  },
  stepNumberCircle: {
    width: 36,
    height: 36,
    borderRadius: 18,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 14,
    marginTop: 2,
  },
  stepNumberText: {
    fontSize: 16,
    fontWeight: '800',
    color: '#FFFFFF',
  },
  stepContent: {
    flex: 1,
  },
  setupStepTitle: {
    fontSize: 17,
    fontWeight: '700',
    color: '#FFFFFF',
    marginBottom: 4,
  },
  setupStepDesc: {
    fontSize: 14,
    color: 'rgba(255,255,255,0.6)',
    lineHeight: 20,
    marginBottom: 12,
  },
  stepDivider: {
    height: 1,
    backgroundColor: 'rgba(255,255,255,0.08)',
    marginVertical: 16,
  },
  setupButton: {
    alignSelf: 'flex-start',
    borderRadius: 12,
    overflow: 'hidden',
    shadowColor: '#7C3AED',
    shadowOffset: {width: 0, height: 4},
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 6,
  },
  setupButtonPressed: {
    opacity: 0.85,
    transform: [{scale: 0.97}],
  },
  setupButtonGradient: {
    paddingVertical: 12,
    paddingHorizontal: 20,
    borderRadius: 12,
  },
  setupButtonText: {
    fontSize: 14,
    fontWeight: '700',
    color: '#FFFFFF',
  },

  // ── Settings Section ───────────────────────────────────
  settingsSection: {
    paddingHorizontal: 24,
    paddingTop: 32,
  },
  settingsCard: {
    backgroundColor: 'rgba(255,255,255,0.08)',
    borderRadius: 20,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.1)',
  },
  settingsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 16,
  },
  settingsRowPressed: {
    backgroundColor: 'rgba(255,255,255,0.05)',
  },
  settingsIconCircle: {
    width: 40,
    height: 40,
    borderRadius: 12,
    backgroundColor: 'rgba(255,255,255,0.1)',
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: 14,
  },
  settingsIcon: {
    fontSize: 18,
  },
  settingsLabel: {
    flex: 1,
    fontSize: 15,
    fontWeight: '600',
    color: '#FFFFFF',
  },
  settingsArrow: {
    fontSize: 22,
    color: 'rgba(255,255,255,0.4)',
    fontWeight: '300',
  },
  settingsDivider: {
    height: 1,
    backgroundColor: 'rgba(255,255,255,0.06)',
    marginLeft: 70,
  },

  // ── Footer ─────────────────────────────────────────────
  footer: {
    paddingVertical: 32,
    alignItems: 'center',
  },
  footerText: {
    fontSize: 13,
    color: 'rgba(255,255,255,0.35)',
  },
});

export default App;
