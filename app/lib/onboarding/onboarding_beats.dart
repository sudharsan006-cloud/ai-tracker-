// Second marks in the video where each of the 5 slides should be "fully shown".
// TUNE THESE after the human watches the real video.
// Slide 1 = card + pulse ring landed, 2 = transactions stacked, 3 = fitness rings filled,
// 4 = budget ring wrapped, 5 = AI bubble on top.
const List<double> kBeatSeconds = [0.0, 2.5, 4.5, 7.0, 9.5];

const List<String> kSlideTitles = [
  'Your money and your health, in one place',
  'Every expense, tracked automatically',
  'Steps, water, workouts. Logged just as easily',
  'Stay inside your budget',
  'AI ties it all together',
];

const List<String> kSlideSubtitles = [
  'Connect your accounts and start tracking.',
  'Add expenses by hand, or import them.',
  'Home fitness tracking, kept simple.',
  'Set limits and see your progress.',
  'Plain-language summaries of your own numbers.',
];
