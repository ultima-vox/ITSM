import { useEffect } from 'react';
import type { Experience } from '@/app/experiences';

export function useExperience(experience: Experience): Experience {
  useEffect(() => {
    document.documentElement.dataset.experience = experience;
  }, [experience]);
  return experience;
}
