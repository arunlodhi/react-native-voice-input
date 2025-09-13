import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  // Speech Recognition Methods
  destroySpeech(callback: (error: string) => void): void;
  startSpeech(
    locale: string,
    opts: Object,
    callback: (error: string) => void,
  ): void;
  stopSpeech(callback: (error: string) => void): void;
  cancelSpeech(callback: (error: string) => void): void;
  isSpeechAvailable(
    callback: (isAvailable: boolean, error: string) => void,
  ): void;
  isRecognizing(callback: (isRecognizing: boolean) => void): void;
  getSpeechRecognitionServices(): Promise<string[]>;

  // Transcription Methods
  destroyTranscription(callback: (error: string) => void): void;
  startTranscription(
    url: string,
    locale: string,
    opts: Object,
    callback: (error: string) => void,
  ): void;
  stopTranscription(callback: (error: string) => void): void;
  cancelTranscription(callback: (error: string) => void): void;

  // Event Emitter Methods
  addListener(eventType: string): void;
  removeListeners(count: number): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('Voice');
