/*

s.boot;

a = Volume(s, 0, s.options.numOutputBusChannels);
a.play;

z = {SinOsc.ar(440, 0, 0.1)}.play

s.volume.volume_(-9);
s.volume.volume;
a.mute;
a.unmute;
a.volume_(0);

a.gui;

a.free;

z.free;

*/

Volume {

	var startBus, numChans, <min, <max, server, persist, <ampSynth, <>window,�<volume, spec;�
	var <lag, sdname, gui, <isPlaying, <muteamp, cpFun, <isMuted=false, <isPrepping;
	
	*new {� arg server, startBus = 0, numChans = 2, min = -90, max = 6, persist = false;
		^super.newCopyArgs(startBus, numChans, min, max, server, persist).initVolume;
	}

	initVolume {
		var cond;
		server = server ?? {Server.default};
		volume = 0;
		isPlaying = false;
		isPrepping = false;
		gui = false;
	}

	sendDef {
		SynthDef(sdname = (\volume_amp_control ++ numChans).asSymbol, 
			{ arg volume_amp = 1, volume_lag = 0.1, volume_gate=1;
			XOut.ar(startBus, 
				Linen.kr(volume_gate, doneAction:2), 
				In.ar(startBus, numChans) * Lag.kr(volume_amp, volume_lag) );
		}).send(server);
	}

	play {arg mute = false;
		var cond;
		cond = Condition.new;
		(isPlaying || isPrepping).not.if({
			server.serverRunning.if({
				Routine.run({
					this.sendDef;
					isPrepping = true;
					server.sync(cond);
					isPlaying = true;
					isPrepping = false;
					cpFun.isNil.if({
						CmdPeriod.add(cpFun = {
							persist.if({
								isPlaying = false;
								this.play(isMuted);
								}, {
								this.free;
								});
							});
						});
					ampSynth = Synth(sdname, [\volume_amp, volume.dbamp, \volume_lag, lag], 
						target: 1, addAction: \addAfter);
					mute.if({this.mute});
					})
				}, {
				"Volume only works on a running Server. Please boot".warn;
				})
			})	
		}
		
	free {
		ampSynth.set(\volume_gate, 0.0);
		isPlaying = false;
		CmdPeriod.remove(cpFun);
		cpFun = nil;
		}
	
	mute{
		this.isPlaying.if({		
			this.prmute;
			}, {
			this.playVolume(true)
			});	
	}
	
	unmute{
		this.prunmute;
		(this.muteamp == 0.0).if({
			this.free;
			});	
	}	

	
	prmute {
		isMuted = true;
		muteamp = volume;
		ampSynth.set([\volume_amp, -inf.dbamp]);
		this.changed(\mute, true);
		}
		
	prunmute {
		isMuted = false;	
		ampSynth.set([\volume_amp, muteamp.dbamp]);
		this.changed(\mute, false);
		
		}
	
	// sets volume back to 1 - removes the synth
	reset {
		this.free;
		}
		
	// cleaner with MVC - in db
	volume_ { arg aVolume;
		volume = aVolume;
		(volume == 0.0).if({
			(this.isPlaying and: {this.isMuted.not}).if({
				this.free; 
				})
			}, {
			server.serverRunning.if({
				this.playVolume(isMuted);
				})
			});
		volume = volume.clip(-90, 6);	
		if(isMuted) { muteamp = volume };
		if(isPlaying && isMuted.not) { ampSynth.set([\volume_amp, volume.dbamp]) };
		this.changed(\amp, volume);
	}
	
	playVolume {arg muted = false;
		(this.isPlaying.not and: {
			(volume != 0.0) or: {muted}
			}).if({
				this.play(muted);
				})
	}
			
	lag_ {arg aLagTime;
		lag = aLagTime;
		ampSynth.set([\volume_lag, lag]);
		}
		
	
	gui {arg window, bounds;
			this.debug(\gui);

		^VolumeGui(this, window, bounds)	
	}
	
	close {
		window.close;
		}

}

VolumeGui{
	var <>model;
	var window, spec, slider, box, simpleController;
	
	*new{|model, win, bounds|	
		^super.new.model_(model).init(win, bounds)
	}
	
	init{|win, bounds|
		spec = [model.min, model.max, \db].asSpec;
		bounds = bounds ?? {Rect(100, 100, 80, 330)};
		window = win ?? {GUI.window.new("Volume", bounds).front};
		box = GUI.numberBox.new(window, Rect(10, 10, 60, 30))
			.value_(model.volume) ;
		slider = GUI.slider.new(window, Rect(10, 40, 60, 280))
			.value_(spec.unmap(model.volume)) ;
		slider.action_({ arg item ;�
			model.volume_(spec.map(item.value));
		}) ;
		box.action_({ arg item ;
			model.volume_(item.value) ;
		}) ;
		window.onClose_({
			simpleController.remove;
		});
		simpleController = SimpleController(model)
				.put(\amp, {|changer, what, volume|
					this.debug(volume);
					box.value_(volume.round(0.01)) ;
					slider.value_(spec.unmap(volume)) ;
				})
	
	}
	
	

}

