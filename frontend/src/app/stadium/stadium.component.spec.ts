import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Stadium1Component } from './stadium.component';

describe('Stadium1Component', () => {
  let component: Stadium1Component;
  let fixture: ComponentFixture<Stadium1Component>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      declarations: [Stadium1Component]
    });
    fixture = TestBed.createComponent(Stadium1Component);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
