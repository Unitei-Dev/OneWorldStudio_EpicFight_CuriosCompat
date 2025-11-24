HOW TO BUILD:
1) Put your jars into /libs:
   - epicfight-forge-20.14.0.1-1.20.1.jar
   - curios-forge-5.14.1+1.20.1.jar
2) Run:
   ./gradlew clean build
3) Take jar from build/libs and put into mods

Notes:
- This mod replaces EpicFight's internal CuriosCompat.
- You MUST delete/disable CuriosCompat inside EpicFight (as you planned).
- Only Curios integration is touched.