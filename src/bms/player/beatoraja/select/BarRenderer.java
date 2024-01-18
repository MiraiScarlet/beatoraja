package bms.player.beatoraja.select;

import static bms.player.beatoraja.SystemSoundManager.SoundType.*;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.*;

import bms.player.beatoraja.input.BMSPlayerInputProcessor;
import bms.player.beatoraja.input.KeyBoardInputProcesseor.ControlKeys;
import bms.player.beatoraja.select.BarManager.*;
import bms.player.beatoraja.select.MusicSelectKeyProperty.MusicSelectKey;
import bms.player.beatoraja.select.bar.*;
import bms.player.beatoraja.skin.*;
import bms.player.beatoraja.skin.Skin.SkinObjectRenderer;
import bms.player.beatoraja.skin.property.EventFactory.EventType;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.*;

import bms.model.Mode;
import bms.player.beatoraja.*;
import bms.player.beatoraja.CourseData.TrophyData;
import bms.player.beatoraja.song.*;
import com.badlogic.gdx.utils.StringBuilder;

/**
 * 楽曲バー描画用クラス
 *
 * @author exch
 */
public class BarRenderer {

	private final MusicSelector select;

	private final BarManager manager;
	
	/**
	 * 現在のフォルダ階層
	 */
	private Queue<DirectoryBar> dir = new Queue<>();
	private String dirString = "";
	/**
	 * 現在表示中のバー一覧
	 */
	private Bar[] currentsongs;
	/**
	 * 選択中のバーのインデックス
	 */
	private int selectedindex;

	private final String[] TROPHY = { "bronzemedal", "silvermedal", "goldmedal" };

	private final int durationlow;
	private final int durationhigh;
	/**
	 * バー移動中のカウンタ
	 */
	private long duration;
	/**
	 * バーの移動方向
	 */
	private int angle;
	private boolean keyinput;

	/**
	 * バー移動中のカウンタ（アナログスクロール）
	 */
	private int analogScrollBuffer = 0;
	private final int analogTicksPerScroll;

	private final int barlength = 60;
	private final BarArea[] bararea;

	private static class BarArea {
		public Bar sd;
		public float x;
		public float y;
		public int value = -1;
		public int text;
	}

	public BarRenderer(MusicSelector select, BarManager manager) {
		this.select = select;
		this.manager = manager;
		
		durationlow = select.resource.getConfig().getScrollDurationLow();
		durationhigh = select.resource.getConfig().getScrollDurationHigh();
		analogTicksPerScroll = select.resource.getConfig().getAnalogTicksPerScroll();

		manager.init();
		
		bararea = Stream.generate(BarArea::new).limit(barlength).toArray(BarArea[]::new);
	}

	public Bar getSelected() {
		return currentsongs != null ? currentsongs[selectedindex] : null;
	}

	public void setSelected(Bar bar) {
		for (int i = 0; i < currentsongs.length; i++) {
			if (currentsongs[i].getTitle().equals(bar.getTitle())) {
				selectedindex = i;
				select.getScoreDataProperty().update(currentsongs[selectedindex].getScore(),
						currentsongs[selectedindex].getRivalScore());
				break;
			}
		}
	}

	public float getSelectedPosition() {
		return ((float) selectedindex) / currentsongs.length;
	}

	public void setSelectedPosition(float value) {
		if (value >= 0 && value < 1) {
			selectedindex = (int) (currentsongs.length * value);
		}
		select.getScoreDataProperty().update(currentsongs[selectedindex].getScore(),
				currentsongs[selectedindex].getRivalScore());
	}

	public void move(boolean inclease) {
		if (inclease) {
			selectedindex++;
		} else {
			selectedindex += currentsongs.length - 1;
		}
		selectedindex = selectedindex % currentsongs.length;
		select.getScoreDataProperty().update(currentsongs[selectedindex].getScore(),
				currentsongs[selectedindex].getRivalScore());
	}

	public void close() {
		if(dir.size == 0) {
			select.executeEvent(EventType.sort);
			return;
		}

		final DirectoryBar current = dir.removeLast();
		final DirectoryBar parent = dir.size > 0 ? dir.last() : null;
		dir.addLast(current);
		updateBar(parent);
		select.play(FOLDER_CLOSE);
	}

	public boolean mousePressed(SkinBar baro, int button, int x, int y) {
		for (int i : ((MusicSelectSkin) select.getSkin()).getClickableBar()) {
			boolean on = (i == ((MusicSelectSkin) select.getSkin()).getCenterBar());
			if (baro.getBarImages(on, i) == null) {
				continue;
			}
			int index = (int) (selectedindex + currentsongs.length * 100 + i
					- ((MusicSelectSkin) select.getSkin()).getCenterBar()) % currentsongs.length;
			Bar sd = currentsongs[index];

			Rectangle r = baro.getBarImages(on, i).getDestination(select.timer.getNowTime(), select);
			if (r != null) {
				if (r != null && r.x <= x && r.x + r.width >= x && r.y <= y && r.y + r.height >= y) {
					if (button == 0) {
						select.select(sd);
					} else {
						close();
					}
					return true;
				}
			}
		}
		return false;
	}

	private long time;
	
	public void prepare(MusicSelectSkin skin, SkinBar baro, long time) {
		this.time = time;		
		final long timeMillis = System.currentTimeMillis();
		boolean applyMovement = duration != 0 && duration > timeMillis;
		float angleLerp = 0;
		if (applyMovement) {
			angleLerp = angle < 0 ? ((float) (timeMillis - duration)) / angle
				: ((float) (duration - timeMillis)) / angle;
		}

		for (int i = 0; i < barlength; i++) {
			// calcurate song bar position
			final BarArea ba = bararea[i];
			boolean on = (i == skin.getCenterBar());
			if (baro.getBarImages(on, i) == null) {
				continue;
			}
			float dx = 0;
			float dy = 0;
			final SkinImage si1 = baro.getBarImages(on, i);
			if (si1.draw) {
				if (applyMovement) {
					int nextindex = i + (angle >= 0 ? 1 : -1);
					SkinImage si2 = nextindex >= 0 ? baro.getBarImages(nextindex == skin.getCenterBar(), nextindex)
							: null;
					if (si2 != null && si2.draw) {
						dx = (si2.region.x - si1.region.x) * Math.max(Math.min(angleLerp, 1), -1);
						dy = (si2.region.y - si1.region.y) * angleLerp;
					}
				}
				ba.x = (int) (si1.region.x + dx);
				ba.y = (int) (si1.region.y + dy + (baro.getPosition() == 1 ? si1.region.height : 0));

				// set song bar type
				int index = (selectedindex + currentsongs.length * 100 + i - skin.getCenterBar())
						% currentsongs.length;
				Bar sd = currentsongs[index];
				ba.sd = sd;

				if (sd instanceof TableBar) {
					ba.value = 2;
				} else if (sd instanceof HashBar) {
					ba.value = 2;
				} else if (sd instanceof GradeBar) {
					ba.value = ((GradeBar) sd).existsAllSongs() ? 3 : 4;
				} else if (sd instanceof RandomCourseBar) {
					ba.value = ((RandomCourseBar) sd).existsAllSongs() ? 2 : 4;
				} else if (sd instanceof FolderBar) {
					ba.value = 1;
				} else if (sd instanceof SongBar) {
					ba.value = ((SongBar) sd).existsSong() ? 0 : 4;
				} else if (sd instanceof SearchWordBar) {
					ba.value = 6;
				} else if (sd instanceof CommandBar || sd instanceof ContainerBar) {
					ba.value = 5;
				} else if (sd instanceof ExecutableBar) {
					ba.value = 2;
				} else {
					ba.value = -1;
				}
			} else {
				ba.value = -1;
			}

			if(ba.value != -1) {
				// Barの種類によってテキストを変える
				// SongBarかFolderBarの場合は新規かどうかでさらに変える
				// songstatus最終値 =
				// 0:通常 1:新規 2:SongBar(通常) 3:SongBar(新規) 4:FolderBar(通常) 5:FolderBar(新規) 6:TableBar or HashBar
				// 7:GradeBar(曲所持) 8:(SongBar or GradeBar)(曲未所持) 9:CommandBar or ContainerBar 10:SearchWordBar
				// 3以降で定義されてなければ0か1を用いる
				int songstatus = ba.value;
				if(songstatus >= 2) {
					songstatus += 4;
					//定義されてなければ0:通常を用いる
					if(baro.getText(songstatus) == null) songstatus = 0;
				} else {
					if (songstatus == 0) {
						SongData song = ((SongBar) ba.sd).getSongData();
						songstatus = song == null || System.currentTimeMillis() / 1000 > song.getAdddate() + 3600 * 24 ? 2 : 3;
						//定義されてなければ0:通常か1:新規を用いる
						if(baro.getText(songstatus) == null) songstatus = songstatus == 3 ? 1 : 0;
					} else {
						FolderData data = ((FolderBar) ba.sd).getFolderData();
						songstatus = data == null || System.currentTimeMillis() / 1000 > data.getAdddate() + 3600 * 24 ? 4 : 5;
						//定義されてなければ0:通常か1:新規を用いる
						if(baro.getText(songstatus) == null) songstatus = songstatus == 5 ? 1 : 0;
					}
				}
				ba.text = songstatus;
			}
		}
	}

	public void render(SkinObjectRenderer sprite, MusicSelectSkin skin, SkinBar baro) {
		if (skin == null) {
			return;
		}

		if (bartextupdate) {
			bartextupdate = false;
			ObjectSet<Character> charset = new ObjectSet<Character>();

			for (Bar song : currentsongs) {
				for (char c : song.getTitle().toCharArray()) {
					charset.add(c);
				}
			}

			char[] chars = new char[charset.size];
			int i = 0;
			for (char c : charset) {
				chars[i++] = c;
			}
			
			for(int index = 0;index < SkinBar.BARTEXT_COUNT;index++) {
				if(baro.getText(index) != null) {
					baro.getText(index).prepareFont(String.valueOf(chars));				
				}				
			}
		}

		// check terminated loader thread and load song images
		if(manager.loader != null && manager.loader.getState() == Thread.State.TERMINATED){
			select.loadSelectedSongImages();
			manager.loader = null;
		}

		// draw song bar
		for (int i = 0; i < barlength; i++) {
			final BarArea ba = bararea[i];
			boolean on = (i == skin.getCenterBar());
			final SkinImage si = baro.getBarImages(on, i);
			if (si == null) {
				continue;
			}

			if (si.draw) {
				float orgx = si.region.x;
				float orgy = si.region.y;
				si.draw(sprite, time, select, ba.value, ba.x - si.region.x, ba.y - si.region.y - (baro.getPosition() == 1 ? si.region.height : 0));
				si.region.x = orgx;
				si.region.y = orgy;
			} else {
				ba.value = -1;
			}
		}

		for (int i = 0; i < barlength; i++) {
			final BarArea ba = bararea[i];
			if(ba.value == -1) {
				continue;
			}
			// folder graph
			if (ba.sd instanceof DirectoryBar) {
				final SkinDistributionGraph graph = baro.getGraph();
				if (graph != null && graph.draw) {
					graph.draw(sprite, (DirectoryBar)ba.sd, ba.x, ba.y);
				}
			}
		}

		for (int i = 0; i < barlength; i++) {
			final BarArea ba = bararea[i];
			if(ba.value == -1) {
				continue;
			}
			final SkinText text = baro.getText(ba.text);
			if(text != null) {
				text.setText(ba.sd.getTitle());
				text.draw(sprite, ba.x, ba.y);				
			}
		}

		for (int i = 0; i < barlength; i++) {
			final BarArea ba = bararea[i];
			if(ba.value == -1) {
				continue;
			}
			// trophy
			if (ba.sd instanceof GradeBar) {
				final TrophyData trophy = ((GradeBar) ba.sd).getTrophy();
				if (trophy != null) {
					for (int j = 0; j < TROPHY.length; j++) {
						if (TROPHY[j].equals(trophy.getName())) {
							final SkinImage trophyImage = baro.getTrophy(j);
							if(trophyImage != null) {
								trophyImage.draw(sprite, ba.x, ba.y);								
							}
							break;
						}
					}
				}
			}
		}

		for (int i = 0; i < barlength; i++) {
			final BarArea ba = bararea[i];
			if(ba.value == -1) {
				continue;
			}
			// lamp
			if(select.getRival() != null) {
				final SkinImage playerLamp = baro.getPlayerLamp(ba.sd.getLamp(true));
				if (playerLamp != null) {
					playerLamp.draw(sprite, ba.x, ba.y);
				}
				final SkinImage rivalLamp = baro.getRivalLamp(ba.sd.getLamp(false));
				if (rivalLamp != null) {
					rivalLamp.draw(sprite, ba.x, ba.y);
				}
			} else {
				final SkinImage lamp = baro.getLamp(ba.sd.getLamp(true));
				if (lamp != null) {
					lamp.draw(sprite, ba.x, ba.y);
				}
			}
		}

		for (int i = 0; i < barlength; i++) {
			final BarArea ba = bararea[i];
			if(ba.value == -1) {
				continue;
			}
			// level
			if (ba.sd instanceof SongBar && ((SongBar) ba.sd).existsSong()) {
				final SongData song = ((SongBar) ba.sd).getSongData();
				final SkinNumber leveln = baro.getBarlevel(song.getDifficulty() >= 0 && song.getDifficulty() < 7
						? song.getDifficulty() : 0);
				if (leveln != null) {
					leveln.draw(sprite, time, song.getLevel(), select, ba.x, ba.y);
				}
			}
		}

		for (int i = 0; i < barlength; i++) {
			final BarArea ba = bararea[i];
			if(ba.value == -1) {
				continue;
			}

			int flag = 0;
			if (ba.sd instanceof SongBar && ((SongBar) ba.sd).existsSong()) {
				SongData song = ((SongBar) ba.sd).getSongData();
				flag |= song.getFeature();
			}

			if (ba.sd instanceof GradeBar) {
				GradeBar gb = (GradeBar) ba.sd;
				if (gb.existsAllSongs()) {
					for (SongData song : gb.getSongDatas()) {
						flag |= song.getFeature();
					}
				}
			}

			// LN
			int ln = -1;
			if((flag & SongData.FEATURE_UNDEFINEDLN) != 0) {
				ln = select.main.getPlayerConfig().getLnmode();
			}
			if((flag & SongData.FEATURE_LONGNOTE) != 0) {
				ln = ln > 0 ? ln : 0;
			}
			if((flag & SongData.FEATURE_CHARGENOTE) != 0) {
				ln = ln > 1 ? ln : 1;
			}
			if((flag & SongData.FEATURE_HELLCHARGENOTE) != 0) {
				ln = ln > 2 ? ln : 2;
			}

			if(ln >= 0) {
				// LNラベル描画分岐
				final int[] lnindex = {0,3,4};
				if (baro.getLabel(lnindex[ln]) != null) {
					baro.getLabel(lnindex[ln]).draw(sprite, ba.x, ba.y);
				} else if (baro.getLabel(0) != null) {
					baro.getLabel(0).draw(sprite, ba.x, ba.y);
				}

			}
			// MINE
			if ((flag & SongData.FEATURE_MINENOTE) != 0 && baro.getLabel(2) != null) {
				baro.getLabel(2).draw(sprite, ba.x, ba.y);
			}
			// RANDOM
			if ((flag & SongData.FEATURE_RANDOM) != 0 && baro.getLabel(1) != null) {
				baro.getLabel(1).draw(sprite, ba.x, ba.y);
			}
		}
	}

	public void input() {
		BMSPlayerInputProcessor input = select.main.getInputProcessor();

        final MusicSelectKeyProperty property = MusicSelectKeyProperty.values()[select.resource.getPlayerConfig().getMusicselectinput()];

		// song bar scroll on mouse wheel
		int mov = -input.getScroll();
		input.resetScroll();

		analogScrollBuffer += property.getAnalogChange(input, MusicSelectKey.UP) - property.getAnalogChange(input, MusicSelectKey.DOWN);
		mov += analogScrollBuffer/analogTicksPerScroll;
		analogScrollBuffer %= analogTicksPerScroll;
		if (mov != 0) {
			// set duration and angle for smooth song bar scroll animation
			long l = System.currentTimeMillis();
			int remainingScroll = angle == 0 ? 0 : (int)Math.max(0, duration - l)/angle;
			remainingScroll = Math.max(Math.min(remainingScroll + mov, 2), -2);
			if (remainingScroll == 0) {
				angle = 0;
				duration = l;
			} else {
				final int scrollDuration = 120/remainingScroll/remainingScroll;
				angle = scrollDuration/remainingScroll;
				duration = l + scrollDuration;
			}
		}

		// song bar scroll
		if (property.isNonAnalogPressed(input, MusicSelectKey.UP, false) || input.getControlKeyState(ControlKeys.DOWN)) {
			long l = System.currentTimeMillis();
			if (duration == 0) {
				keyinput = true;
				mov = 1;
				duration = l + durationlow;
				angle = durationlow;
			}
			if (l > duration && keyinput == true) {
				duration = l + durationhigh;
				mov = 1;
				angle = durationhigh;
			}
		} else if (property.isNonAnalogPressed(input, MusicSelectKey.DOWN, false) || input.getControlKeyState(ControlKeys.UP)) {
			long l = System.currentTimeMillis();
			if (duration == 0) {
				keyinput = true;
				mov = -1;
				duration = l + durationlow;
				angle = -durationlow;
			}
			if (l > duration && keyinput == true) {
				duration = l + durationhigh;
				mov = -1;
				angle = -durationhigh;
			}
		} else {
			keyinput = false;
		}
		long l = System.currentTimeMillis();
		if (l > duration && keyinput == false) {
			duration = 0;
		}
		while(mov > 0) {
			move(true);
			select.play(SCRATCH);
			mov--;
		}
		while(mov < 0) {
			move(false);
			select.play(SCRATCH);
			mov++;
		}
	}

	public void resetInput() {
		long l = System.currentTimeMillis();
		if (l > duration) {
			duration = 0;
		}
	}

	private boolean bartextupdate = false;

	public Queue<DirectoryBar> getDirectory() {
		return dir;
	}

	public String getDirectoryString() {
		return dirString;
	}

	public boolean updateBar() {
		if (dir.size > 0) {
			return updateBar(dir.last());
		}
		return updateBar(null);
	}

	public boolean updateBar(Bar bar) {
		Bar prevbar = currentsongs != null ? currentsongs[selectedindex] : null;
		int prevdirsize = dir.size;
		Bar sourcebar = null;
		Array<Bar> l = new Array<Bar>();
		boolean showInvisibleCharts = false;
		boolean isSortable = true;

		if (MainLoader.getIllegalSongCount() > 0) {
			l.addAll(SongBar.toSongBarArray(select.getSongDatabase().getSongDatas(MainLoader.getIllegalSongs())));
		} else if (bar == null) {
			// root bar
			if (dir.size > 0) {
				prevbar = dir.first();
			}
			dir.clear();
			manager.sourcebars.clear();
			l.addAll(new FolderBar(select, null, "e2977170").getChildren());
			l.add(manager.courses);
			l.addAll(manager.favorites);
			manager.appendFolders.keySet().forEach((key) -> {
			    l.add(manager.appendFolders.get(key));
			});
			l.addAll(manager.tables);
			l.addAll(manager.commands);
			l.addAll(manager.search);
		} else if (bar instanceof DirectoryBar) {
			showInvisibleCharts = ((DirectoryBar)bar).isShowInvisibleChart();
			if(dir.indexOf((DirectoryBar) bar, true) != -1) {
				while(dir.last() != bar) {
					prevbar = dir.removeLast();
					sourcebar = manager.sourcebars.removeLast();
				}
				dir.removeLast();
			}
			l.addAll(((DirectoryBar) bar).getChildren());
			isSortable = ((DirectoryBar) bar).isSortable();

			if (bar instanceof ContainerBar && manager.randomCourseResult.size > 0) {
				StringBuilder str = new StringBuilder();
				for (Bar b : dir) {
					str.append(b.getTitle()).append(" > ");
				}
				str.append(bar.getTitle()).append(" > ");
				final String ds = str.toString();
				for (RandomCourseResult r : manager.randomCourseResult) {
					if (r.dirString.equals(ds)) {
						l.add(r.course);
					}
				}
			}
		}

		if(!select.resource.getConfig().isShowNoSongExistingBar()) {
			Array<Bar> remove = new Array<Bar>();
			for (Bar b : l) {
				if ((b instanceof SongBar && !((SongBar) b).existsSong())
					|| b instanceof GradeBar && !((GradeBar) b).existsAllSongs()) {
					remove.add(b);
				}
			}
			l.removeAll(remove, true);
		}

		if (l.size > 0) {
			final PlayerConfig config = select.resource.getPlayerConfig();
			int modeIndex = 0;
			for(;modeIndex < MusicSelector.MODE.length && MusicSelector.MODE[modeIndex] != config.getMode();modeIndex++);
			for(int trialCount = 0; trialCount < MusicSelector.MODE.length; trialCount++, modeIndex++) {
				final Mode mode = MusicSelector.MODE[modeIndex % MusicSelector.MODE.length];
				config.setMode(mode);
				Array<Bar> remove = new Array<Bar>();
				for (Bar b : l) {
					if(b instanceof SongBar && ((SongBar) b).getSongData() != null) {
						final SongData song = ((SongBar) b).getSongData();
						if((!showInvisibleCharts && (song.getFavorite() & (SongData.INVISIBLE_SONG | SongData.INVISIBLE_CHART)) != 0)
								|| (mode != null && song.getMode() != 0 && song.getMode() != mode.id)) {
							remove.add(b);
						}
					}
				}
				if(l.size != remove.size) {
					l.removeAll(remove, true);
					break;
				}
			}

			if (bar != null) {
				dir.addLast((DirectoryBar) bar);
				if (dir.size > prevdirsize) {
					manager.sourcebars.addLast(prevbar);
				}
			}

			Bar[] newcurrentsongs = l.toArray(Bar.class);
			for (Bar b : newcurrentsongs) {
				if (b instanceof SongBar) {
					SongData sd = ((SongBar) b).getSongData();
					if (sd != null && select.getScoreDataCache().existsScoreDataCache(sd, config.getLnmode())) {
						b.setScore(select.getScoreDataCache().readScoreData(sd, config.getLnmode()));
					}
				}
			}

			if(isSortable) {
			    Sort.instance().sort(newcurrentsongs, BarSorter.defaultSorter[select.getSort()].sorter);
			}

			Array<Bar> bars = new Array<Bar>();
			if (select.main.getPlayerConfig().isRandomSelect()) {
				try {
					for (RandomFolder randomFolder : manager.randomFolderList) {
						SongData[] randomTargets = Stream.of(newcurrentsongs).filter(
								songBar -> songBar instanceof SongBar && ((SongBar) songBar).getSongData().getPath() != null)
								.map(songBar -> ((SongBar) songBar).getSongData()).toArray(SongData[]::new);
						if (randomFolder.getFilter() != null) {
							Set<String> filterKey = randomFolder.getFilter().keySet();
							randomTargets = Stream.of(randomTargets).filter(r -> {
								ScoreData scoreData = select.getScoreDataCache().readScoreData(r, config.getLnmode());
								for (String key : filterKey) {
									String getterMethodName = "get" + key.substring(0, 1).toUpperCase()
											+ key.substring(1);
									try {
										Object value = randomFolder.getFilter().get(key);
										if (scoreData == null) {
											if (value instanceof String && !"".equals((String) value)) {
												return false;
											}
											if (value instanceof Integer && 0 != (Integer) value) {
												return false;
											}
										} else {
											Method getterMethod = ScoreData.class.getMethod(getterMethodName);
											Object propertyValue = getterMethod.invoke(scoreData);
											if (!propertyValue.equals(value)) {
												return false;
											}
										}
									} catch (Throwable e) {
										e.printStackTrace();
										return false;
									}
								}
								return true;
							}).toArray(SongData[]::new);
						}
						if ((randomFolder.getFilter() != null && randomTargets.length >= 1)
								|| (randomFolder.getFilter() == null && randomTargets.length >= 2)) {
							Bar randomBar = new ExecutableBar(randomTargets, select.main.getCurrentState(),
									randomFolder.getName());
							bars.add(randomBar);
						}
					}
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}

			bars.addAll(newcurrentsongs);

			currentsongs = bars.toArray(Bar.class);
			bartextupdate = true;

			selectedindex = 0;

			// 変更前と同じバーがあればカーソル位置を保持する
			if (sourcebar != null) {
				prevbar = sourcebar;
			}
			if (prevbar != null) {
				if (prevbar instanceof SongBar && ((SongBar) prevbar).existsSong()) {
					final SongBar prevsong = (SongBar) prevbar;
					for (int i = 0; i < currentsongs.length; i++) {
						if (currentsongs[i] instanceof SongBar && ((SongBar) currentsongs[i]).existsSong() &&
								((SongBar) currentsongs[i]).getSongData().getSha256()
								.equals(prevsong.getSongData().getSha256())) {
							selectedindex = i;
							break;
						}
					}
				} else {
					for (int i = 0; i < currentsongs.length; i++) {
						if (currentsongs[i].getClass() == prevbar.getClass() && currentsongs[i].getTitle().equals(prevbar.getTitle())) {
							selectedindex = i;
							break;
						}
					}

				}
			}

			if (manager.loader != null) {
				manager.loader.stopRunning();
			}
			manager.loader = new BarContentsLoaderThread(select, currentsongs);
			manager.loader.start();
			select.getScoreDataProperty().update(currentsongs[selectedindex].getScore(),
					currentsongs[selectedindex].getRivalScore());

			StringBuilder str = new StringBuilder();
			for (Bar b : dir) {
				str.append(b.getTitle()).append(" > ");
			}
			dirString = str.toString();

			select.selectedBarMoved();

			return true;
		}

		if (dir.size > 0) {
			updateBar(dir.last());
		} else {
			updateBar(null);
		}
		Logger.getGlobal().warning("楽曲がありません");
		return false;
	}

	public void dispose() {
		// favorite書き込み
//		CourseData course = new CourseData();
//		course.setName(favorites[0].getTitle());
//		List<String> l = new ArrayList<>();
//		for(TableData.TableSongData element : favorites[0].getElements()) {
//			l.add(element.getHash());
//		}
//		course.setHash(l.toArray(new String[l.size()]));
//		CourseDataAccessor cda = new CourseDataAccessor("favorite");
//		if(!Files.exists(Paths.get("favorite"))) {
//			try {
//				Files.createDirectory(Paths.get("favorite"));
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//		}
//		cda.write("default", course);
	}

}
